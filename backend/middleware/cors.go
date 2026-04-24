package middleware

import (
	"github.com/gin-gonic/gin"
)

// CORS 允许管理后台（如 center 域名）跨域访问 API（api2 域名）。
// 浏览器预检会带 Access-Control-Request-Headers，需原样回显，否则部分环境（尤其 Safari）会拦截。
func CORS() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
		if reqH := c.GetHeader("Access-Control-Request-Headers"); reqH != "" {
			c.Header("Access-Control-Allow-Headers", reqH)
		} else {
			c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Accept, Authorization, X-Requested-With")
		}
		c.Header("Access-Control-Max-Age", "86400")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	}
}
