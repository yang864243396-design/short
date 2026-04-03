package middleware

import (
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"short-drama-backend/config"
	"short-drama-backend/utils"
)

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
			return []byte(cfg.JWT.Secret), nil
		})

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

		userID := uint(claims["user_id"].(float64))
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
			return []byte(cfg.JWT.Secret), nil
		})

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

		userID := uint(claims["user_id"].(float64))
		var roleID uint
		if rid, ok := claims["role_id"].(float64); ok {
			roleID = uint(rid)
		}
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
