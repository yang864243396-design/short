package handlers

import (
	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
	"short-drama-backend/database"
	"short-drama-backend/middleware"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

type RegisterRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required,min=6"`
	Nickname string `json:"nickname"`
}

type LoginRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
}

func Register(c *gin.Context) {
	var req RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}

	var existing models.AppUser
	if database.DB.Where("username = ?", req.Username).First(&existing).RowsAffected > 0 {
		utils.BadRequest(c, "用户名已存在")
		return
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		utils.ServerError(c, "服务器错误")
		return
	}

	nickname := req.Nickname
	if nickname == "" {
		nickname = req.Username
	}

	user := models.AppUser{
		Username:     req.Username,
		Nickname:     nickname,
		PasswordHash: string(hash),
		Coins:        100,
	}
	database.DB.Create(&user)

	token, err := middleware.GenerateToken(user.ID)
	if err != nil {
		utils.ServerError(c, "生成Token失败")
		return
	}

	utils.Success(c, gin.H{"token": token, "user": user})
}

func Login(c *gin.Context) {
	var req LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}

	var user models.AppUser
	if database.DB.Where("username = ?", req.Username).First(&user).RowsAffected == 0 {
		utils.BadRequest(c, "用户名或密码错误")
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)); err != nil {
		utils.BadRequest(c, "用户名或密码错误")
		return
	}

	token, err := middleware.GenerateToken(user.ID)
	if err != nil {
		utils.ServerError(c, "生成Token失败")
		return
	}

	utils.Success(c, gin.H{"token": token, "user": user})
}
