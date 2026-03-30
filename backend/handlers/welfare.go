package handlers

import (
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func GetWelfareTasks(c *gin.Context) {
	var tasks []models.WelfareTask
	database.DB.Find(&tasks)
	utils.Success(c, tasks)
}

func DailyCheckin(c *gin.Context) {
	userID := c.GetUint("user_id")

	today := time.Now().Format("2006-01-02")
	var existing models.CheckIn
	result := database.DB.Where("user_id = ? AND DATE(created_at) = ?", userID, today).First(&existing)

	if result.RowsAffected > 0 {
		utils.BadRequest(c, "今日已签到")
		return
	}

	coins := 50
	checkin := models.CheckIn{
		UserID: userID,
		Coins:  coins,
	}
	database.DB.Create(&checkin)

	database.DB.Model(&models.User{}).Where("id = ?", userID).
		UpdateColumn("coins", database.DB.Raw("coins + ?", coins))

	var user models.User
	database.DB.First(&user, userID)

	utils.Success(c, gin.H{
		"coins_earned": coins,
		"total_coins":  user.Coins,
		"message":      "签到成功",
	})
}
