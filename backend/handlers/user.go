package handlers

import (
	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func GetProfile(c *gin.Context) {
	userID := c.GetUint("user_id")
	var user models.User
	if err := database.DB.First(&user, userID).Error; err != nil {
		utils.BadRequest(c, "用户不存在")
		return
	}
	utils.Success(c, user)
}

func UpdateProfile(c *gin.Context) {
	userID := c.GetUint("user_id")
	var req struct {
		Nickname string `json:"nickname"`
		Avatar   string `json:"avatar"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}

	updates := map[string]interface{}{}
	if req.Nickname != "" {
		updates["nickname"] = req.Nickname
	}
	if req.Avatar != "" {
		updates["avatar"] = req.Avatar
	}

	database.DB.Model(&models.User{}).Where("id = ?", userID).Updates(updates)

	var user models.User
	database.DB.First(&user, userID)
	utils.Success(c, user)
}

func GetHistory(c *gin.Context) {
	userID := c.GetUint("user_id")
	var histories []models.UserHistory
	database.DB.Where("user_id = ?", userID).Order("updated_at DESC").Limit(50).Find(&histories)
	utils.Success(c, histories)
}

func GetCollections(c *gin.Context) {
	userID := c.GetUint("user_id")
	var collects []models.UserCollect
	database.DB.Where("user_id = ?", userID).Order("created_at DESC").Find(&collects)

	var dramaIDs []uint
	for _, col := range collects {
		dramaIDs = append(dramaIDs, col.DramaID)
	}

	var dramas []models.Drama
	if len(dramaIDs) > 0 {
		database.DB.Where("id IN ?", dramaIDs).Find(&dramas)
	}
	utils.Success(c, dramas)
}
