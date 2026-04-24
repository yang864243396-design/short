package handlers

import (
	"strings"

	"github.com/gin-gonic/gin"
)

// publicAPIBase 用于拼 notify 等**对外绝对 URL**。
// 1) 若配置了 config.APIPublicBase 则仍优先（显式覆盖）。
// 2) 否则用当前请求推导：与客户端访问本 API 的协议+主机一致，无需再配第二套域名。
// 反代时请在 Nginx 等传 X-Forwarded-Proto、必要时 X-Forwarded-Host。
func publicAPIBase(c *gin.Context, configuredBase string) string {
	s := strings.TrimSpace(configuredBase)
	s = strings.TrimRight(s, "/")
	if s != "" {
		return s
	}
	if c == nil {
		return ""
	}
	scheme := "http"
	if c.Request.TLS != nil {
		scheme = "https"
	}
	if p := strings.ToLower(strings.TrimSpace(c.GetHeader("X-Forwarded-Proto"))); p == "https" || p == "http" {
		scheme = p
	}
	host := strings.TrimSpace(c.Request.Host)
	if h := c.GetHeader("X-Forwarded-Host"); h != "" {
		// 多级反代时取第一段
		host = strings.TrimSpace(strings.Split(h, ",")[0])
	}
	if host == "" {
		return ""
	}
	return scheme + "://" + host
}
