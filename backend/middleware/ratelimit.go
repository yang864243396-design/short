package middleware

import (
	"net/http"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
)

type visitor struct {
	count    int
	lastSeen time.Time
}

var (
	visitors = make(map[string]*visitor)
	mu       sync.Mutex
)

func RateLimit(maxRequests int, window time.Duration) gin.HandlerFunc {
	go cleanupVisitors(window)

	return func(c *gin.Context) {
		ip := c.ClientIP()

		mu.Lock()
		v, exists := visitors[ip]
		if !exists {
			visitors[ip] = &visitor{count: 1, lastSeen: time.Now()}
			mu.Unlock()
			c.Next()
			return
		}

		if time.Since(v.lastSeen) > window {
			v.count = 1
			v.lastSeen = time.Now()
		} else {
			v.count++
		}

		if v.count > maxRequests {
			mu.Unlock()
			c.JSON(http.StatusTooManyRequests, gin.H{
				"code":    429,
				"message": "请求过于频繁，请稍后再试",
			})
			c.Abort()
			return
		}

		mu.Unlock()
		c.Next()
	}
}

func cleanupVisitors(window time.Duration) {
	for {
		time.Sleep(window)
		mu.Lock()
		for ip, v := range visitors {
			if time.Since(v.lastSeen) > window*2 {
				delete(visitors, ip)
			}
		}
		mu.Unlock()
	}
}
