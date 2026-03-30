package main

import (
	"fmt"

	"short-drama-backend/config"
	"short-drama-backend/cron"
	"short-drama-backend/database"
	"short-drama-backend/utils"
)

func main() {
	cfg := config.Load()
	database.Init(cfg.DB)
	utils.InitRedis(cfg.Redis)

	fmt.Println("Running RefreshAllRankings...")
	cron.RefreshAllRankings()

	fmt.Println("\nVerifying Redis...")
	keys := []string{"ranking:hot:full", "ranking:rising:full", "ranking:rating:full"}
	for _, k := range keys {
		var data []map[string]interface{}
		err := utils.CacheGet(k, &data)
		if err != nil {
			fmt.Printf("  %s: ERROR %v\n", k, err)
			continue
		}
		fmt.Printf("  %s: %d items\n", k, len(data))
		for i, item := range data {
			if i >= 2 { break }
			drama, _ := item["drama"].(map[string]interface{})
			fmt.Printf("    %d. %v cover=%v\n", i+1, drama["title"], drama["cover_url"])
		}
	}
}
