package main

import (
	"fmt"
	"log"

	"github.com/gin-gonic/gin"
	"short-drama-backend/config"
	"short-drama-backend/cron"
	"short-drama-backend/database"
	"short-drama-backend/routes"
	"short-drama-backend/utils"
)

func main() {
	cfg := config.Load()

	database.Init(cfg.DB)
	database.Seed()

	utils.InitRedis(cfg.Redis)

	cron.StartRankingCron()

	r := gin.Default()
	routes.Setup(r)

	port := ":8080"
	fmt.Printf("Server running on http://localhost%s\n", port)
	if err := r.Run(port); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
