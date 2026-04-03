package main

import (
	"fmt"
	"golang.org/x/crypto/bcrypt"
	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/models"
)

func main() {
	cfg := config.Load()
	database.Init(cfg.DB)

	username := "admin"
	password := "admin123"

	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		fmt.Println("生成密码失败:", err)
		return
	}

	var existing models.Admin
	result := database.DB.Where("username = ?", username).First(&existing)
	if result.RowsAffected > 0 {
		database.DB.Model(&existing).Update("password_hash", string(hash))
		fmt.Printf("管理员已存在，密码已重置\n")
	} else {
		admin := models.Admin{
			Username:     username,
			Nickname:     "管理员",
			PasswordHash: string(hash),
			Status:       1,
		}
		database.DB.Create(&admin)
		fmt.Printf("管理员创建成功\n")
	}

	fmt.Printf("用户名: %s\n密码: %s\n", username, password)
}
