package middleware

import (
	"fmt"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

// uintFromClaim 解析 JWT MapClaims 中的无符号整型；JSON 数字解码为 float64，异常类型不 panic
func uintFromClaim(claims jwt.MapClaims, key string) (uint, bool) {
	raw, ok := claims[key]
	if !ok {
		return 0, false
	}
	f, ok := raw.(float64)
	if !ok {
		return 0, false
	}
	if f < 0 || f > float64(^uint(0)) || f != float64(uint(f)) {
		return 0, false
	}
	return uint(f), true
}

func Auth() gin.HandlerFunc {
	return func(c *gin.Context) {
		header := c.GetHeader("Authorization")
		if header == "" || !strings.HasPrefix(header, "Bearer ") {
			utils.Unauthorized(c)
			c.Abort()
			return
		}

		tokenStr := strings.TrimPrefix(header, "Bearer ")
		cfg := config.Load()

		token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
			if t.Method != jwt.SigningMethodHS256 {
				return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
			}
			return []byte(cfg.JWT.Secret), nil
		}, jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}))

		if err != nil || !token.Valid {
			utils.Unauthorized(c)
			c.Abort()
			return
		}

		claims, ok := token.Claims.(jwt.MapClaims)
		if !ok {
			utils.Unauthorized(c)
			c.Abort()
			return
		}

		userID, ok := uintFromClaim(claims, "user_id")
		if !ok {
			utils.Unauthorized(c)
			c.Abort()
			return
		}
		var u models.AppUser
		if err := database.DB.Select("id", "deleted_at").First(&u, userID).Error; err != nil || u.DeletedAt != nil {
			utils.Unauthorized(c)
			c.Abort()
			return
		}
		c.Set("user_id", userID)
		c.Next()
	}
}

func GenerateToken(userID uint) (string, error) {
	cfg := config.Load()
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"user_id": userID,
		"exp":     time.Now().Add(time.Duration(cfg.JWT.Expire) * time.Hour).Unix(),
		"iat":     time.Now().Unix(),
	})
	return token.SignedString([]byte(cfg.JWT.Secret))
}

func AdminAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		header := c.GetHeader("Authorization")
		if header == "" || !strings.HasPrefix(header, "Bearer ") {
			utils.Unauthorized(c)
			c.Abort()
			return
		}

		tokenStr := strings.TrimPrefix(header, "Bearer ")
		cfg := config.Load()

		token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
			if t.Method != jwt.SigningMethodHS256 {
				return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
			}
			return []byte(cfg.JWT.Secret), nil
		}, jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}))

		if err != nil || !token.Valid {
			utils.Unauthorized(c)
			c.Abort()
			return
		}

		claims, ok := token.Claims.(jwt.MapClaims)
		if !ok {
			utils.Unauthorized(c)
			c.Abort()
			return
		}

		role, _ := claims["role"].(string)
		if role != "admin" {
			utils.Error(c, 403, "无管理员权限")
			c.Abort()
			return
		}

		userID, ok := uintFromClaim(claims, "user_id")
		if !ok {
			utils.Unauthorized(c)
			c.Abort()
			return
		}
		roleID, _ := uintFromClaim(claims, "role_id")
		c.Set("user_id", userID)
		c.Set("role", role)
		c.Set("role_id", roleID)
		c.Next()
	}
}

func GenerateAdminToken(userID uint, roleID uint) (string, error) {
	cfg := config.Load()
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"user_id": userID,
		"role":    "admin",
		"role_id": roleID,
		"exp":     time.Now().Add(time.Duration(cfg.JWT.Expire) * time.Hour).Unix(),
		"iat":     time.Now().Unix(),
	})
	return token.SignedString([]byte(cfg.JWT.Secret))
}
