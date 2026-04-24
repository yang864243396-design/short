package middleware

import (
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/models"
)

// OptionalAuth 若请求携带有效 App Bearer Token，则写入 user_id；否则不拦截，用于搜索等「可选登录」接口。
func OptionalAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		header := c.GetHeader("Authorization")
		if header == "" || !strings.HasPrefix(header, "Bearer ") {
			c.Next()
			return
		}
		tokenStr := strings.TrimPrefix(header, "Bearer ")
		cfg := config.Load()
		token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
			return []byte(cfg.JWT.Secret), nil
		})
		if err != nil || !token.Valid {
			c.Next()
			return
		}
		claims, ok := token.Claims.(jwt.MapClaims)
		if !ok {
			c.Next()
			return
		}
		if uid, ok := claims["user_id"].(float64); ok {
			uidu := uint(uid)
			var u models.AppUser
			if err := database.DB.Select("id", "deleted_at").First(&u, uidu).Error; err == nil && u.DeletedAt == nil {
				c.Set("user_id", uidu)
			}
		}
		c.Next()
	}
}
