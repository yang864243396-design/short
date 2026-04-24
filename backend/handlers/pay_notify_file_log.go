package handlers

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"short-drama-backend/config"
)

// 支付回调独立日志（仅此类日志写入该文件，不混入 backend_startup.log）
var (
	payNotifyLogOnce  sync.Once
	payNotifyFileLog  *log.Logger
	payNotifyFilePath string
	payNotifyFileErr  error
)

func payNotifyLogPath(c *config.Config) string {
	if c == nil {
		return "logs/pay_notify.log"
	}
	p := strings.TrimSpace(c.PayNotifyLogFile)
	if p == "" {
		return "logs/pay_notify.log"
	}
	return p
}


// payNotifyPrintf 将一行写入 pay_notify 专用文件；写文件失败时回退到标准 log。
func payNotifyPrintf(cfg *config.Config, format string, args ...interface{}) {
	rel := payNotifyLogPath(cfg)
	payNotifyLogOnce.Do(func() {
		abs, err := filepath.Abs(rel)
		if err != nil {
			abs = rel
		}
		payNotifyFilePath = abs
		if err = os.MkdirAll(filepath.Dir(abs), 0755); err != nil {
			payNotifyFileErr = err
			return
		}
		f, err := os.OpenFile(abs, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
		if err != nil {
			payNotifyFileErr = err
			return
		}
		payNotifyFileLog = log.New(f, "", log.LstdFlags|log.Lmicroseconds)
	})
	line := fmt.Sprintf(format, args...)
	if payNotifyFileLog != nil {
		payNotifyFileLog.Println(line)
		return
	}
	log.Printf("[pay/notify] file miss path=%q err=%v | %s", payNotifyFilePath, payNotifyFileErr, line)
}
