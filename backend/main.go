package main

import (
	"fmt"
	"io"
	"log"
	"os"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/joho/godotenv"
	"short-drama-backend/config"
	"short-drama-backend/cron"
	"short-drama-backend/database"
	"short-drama-backend/routes"
	"short-drama-backend/utils"
)

func main() {
	_ = godotenv.Load()
	// 同时写 stdout + 工作目录下 backend_startup.log，宝塔里可直接打开文件看崩溃原因（log.Fatalf 时 defer 不执行，但写入已落盘）。
	var logOut io.Writer = os.Stdout
	if f, err := os.OpenFile("backend_startup.log", os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644); err == nil {
		_, _ = fmt.Fprintf(f, "\n======== short 启动 %s ========\n", time.Now().Format("2006-01-02 15:04:05"))
		logOut = io.MultiWriter(os.Stdout, f)
	}
	log.SetOutput(logOut)

	if os.Getenv("GIN_MODE") == "release" {
		gin.SetMode(gin.ReleaseMode)
	}

	cfg := config.Load()
	if wd, err := os.Getwd(); err == nil {
		log.Printf("working_directory=%s", wd)
	}
	log.Printf("pay_notify_log_file=%s (环境变量 PAY_NOTIFY_LOG_FILE 可覆盖相对或绝对路径)", cfg.PayNotifyLogPathDisplay())

	database.Init(cfg.DB)
	database.Seed()

	utils.InitRedis(cfg.Redis)

	cron.StartRankingCron()

	r := gin.Default()
	routes.Setup(r)

	addr := config.ListenAddr()
	fmt.Printf("Server running on http://localhost%s (GIN_MODE=%s)\n", addr, gin.Mode())
	if err := r.Run(addr); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
