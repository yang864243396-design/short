package main

import (
	"fmt"
	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/models"
)

func main() {
	cfg := config.Load()
	database.Init(cfg.DB)

	fmt.Println("Dropping and recreating drama_stats table...")
	database.DB.Migrator().DropTable(&models.DramaStats{})
	database.DB.AutoMigrate(&models.DramaStats{})

	fmt.Println("Verifying columns...")
	type Col struct {
		Field string
		Type  string
	}
	var cols []Col
	database.DB.Raw("SHOW COLUMNS FROM drama_stats").Scan(&cols)
	for _, c := range cols {
		fmt.Printf("  %s (%s)\n", c.Field, c.Type)
	}

	fmt.Println("Done!")
}
