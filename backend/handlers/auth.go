package handlers

import (
	"crypto/rand"
	"errors"
	"fmt"
	"log"
	"math/big"
	"regexp"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"

	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/middleware"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

var emailRegexp = regexp.MustCompile(`^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$`)

type sendRegisterCodeRequest struct {
	Email string `json:"email" binding:"required"`
}

// SendRegisterCode 发送邮箱注册验证码（需先配置 SMTP_* 环境变量或 .env）
func SendRegisterCode(c *gin.Context) {
	var req sendRegisterCodeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[SendRegisterCode] bind err=%v", err)
		utils.BadRequest(c, "参数错误")
		return
	}
	email := strings.TrimSpace(strings.ToLower(req.Email))
	if !emailRegexp.MatchString(email) {
		utils.BadRequest(c, "邮箱格式不正确")
		return
	}

	cfg := config.Load()
	if cfg.SMTP.Host == "" || cfg.SMTP.User == "" || cfg.SMTP.Password == "" || cfg.SMTP.From == "" {
		log.Printf("[SendRegisterCode] smtp not configured host_empty=%v user_empty=%v pass_empty=%v from_empty=%v",
			cfg.SMTP.Host == "", cfg.SMTP.User == "", cfg.SMTP.Password == "", cfg.SMTP.From == "")
		utils.ServerError(c, "邮件服务未配置（需设置 SMTP_HOST、SMTP_USER、SMTP_PASSWORD、SMTP_FROM）")
		return
	}

	var n int64
	database.DB.Model(&models.AppUser{}).Where("username = ? AND deleted_at IS NULL", email).Count(&n)
	if n > 0 {
		utils.BadRequest(c, "该邮箱已注册")
		return
	}

	coolKey := "register_send:" + email
	if _, err := utils.Rdb.Get(utils.Ctx, coolKey).Result(); err == nil {
		utils.BadRequest(c, "发送过于频繁，请稍后再试")
		return
	}

	code, err := randomDigits6()
	if err != nil {
		utils.ServerError(c, "生成验证码失败")
		return
	}

	codeKey := "register_code:" + email
	if err := utils.Rdb.Set(utils.Ctx, codeKey, code, 10*time.Minute).Err(); err != nil {
		// 与启动时 InitRedis Ping 失败不同：多为运行中 Redis 挂了或超时
		log.Printf("[SendRegisterCode] redis SET register_code err=%v email=%s", err, email)
		utils.ServerError(c, "验证码服务暂不可用，请稍后重试")
		return
	}
	_ = utils.Rdb.Set(utils.Ctx, coolKey, "1", 60*time.Second).Err()

	if err := utils.SendRegisterVerificationEmail(&cfg.SMTP, email, code); err != nil {
		utils.Rdb.Del(utils.Ctx, codeKey)
		log.Printf("[SendRegisterCode] smtp to=%s err=%v", email, err)
		utils.ServerError(c, "邮件发送失败，请检查 SMTP 配置与服务器出站端口（465/587）")
		return
	}

	utils.Success(c, nil)
}

func randomDigits6() (string, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(900000))
	if err != nil {
		return "", err
	}
	num := int(n.Int64()) + 100000
	return fmt.Sprintf("%06d", num), nil
}

type RegisterRequest struct {
	Email    string `json:"email" binding:"required"`
	Password string `json:"password" binding:"required,min=6"`
	Code     string `json:"code" binding:"required"`
	Nickname string `json:"nickname"`
}

func Register(c *gin.Context) {
	var req RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	email := strings.TrimSpace(strings.ToLower(req.Email))
	if !emailRegexp.MatchString(email) {
		utils.BadRequest(c, "邮箱格式不正确")
		return
	}

	stored, err := utils.Rdb.Get(utils.Ctx, "register_code:"+email).Result()
	if err != nil || stored != strings.TrimSpace(req.Code) {
		utils.BadRequest(c, "验证码错误或已过期")
		return
	}
	utils.Rdb.Del(utils.Ctx, "register_code:"+email)

	var existing models.AppUser
	if database.DB.Where("username = ? AND deleted_at IS NULL", email).First(&existing).RowsAffected > 0 {
		utils.BadRequest(c, "该邮箱已注册")
		return
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		utils.ServerError(c, "服务器错误")
		return
	}

	nickname := strings.TrimSpace(req.Nickname)
	if nickname == "" {
		nickname = strings.Split(email, "@")[0]
	}

	user := models.AppUser{
		Username:        email,
		RegisteredEmail: email,
		Nickname:        nickname,
		PasswordHash:    string(hash),
		Coins:           0,
	}
	if err := database.DB.Create(&user).Error; err != nil {
		utils.ServerError(c, "注册失败")
		return
	}

	token, err := middleware.GenerateToken(user.ID)
	if err != nil {
		utils.ServerError(c, "生成Token失败")
		return
	}

	utils.Success(c, gin.H{"token": token, "user": user})
}

type LoginRequest struct {
	Email    string `json:"email" binding:"required"`
	Password string `json:"password" binding:"required"`
}

func Login(c *gin.Context) {
	var req LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	email := strings.TrimSpace(strings.ToLower(req.Email))

	var user models.AppUser
	if err := database.DB.Where("username = ? AND deleted_at IS NULL", email).First(&user).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			var deletedUser models.AppUser
			if database.DB.Where("registered_email = ? AND deleted_at IS NOT NULL", email).First(&deletedUser).Error == nil {
				utils.BadRequest(c, "该账号已注销，无法登录")
				return
			}
			utils.BadRequest(c, "邮箱或密码错误")
			return
		}
		utils.ServerError(c, "登录失败")
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)); err != nil {
		utils.BadRequest(c, "邮箱或密码错误")
		return
	}

	token, err := middleware.GenerateToken(user.ID)
	if err != nil {
		utils.ServerError(c, "生成Token失败")
		return
	}

	utils.Success(c, gin.H{"token": token, "user": user})
}
