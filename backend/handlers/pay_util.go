package handlers

import (
	"short-drama-backend/database"
)

// scanAppUserCoins 读 app_users.coins，失败时为 0（与原 Scan 未检查 Error 行为一致）
func scanAppUserCoins(userID uint) int {
	var coins int
	_ = database.DB.Table("app_users").Select("coins").Where("id = ?", userID).Scan(&coins)
	return coins
}
