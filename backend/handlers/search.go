package handlers

import (
	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func GetHotSearch(c *gin.Context) {
	var hotSearches []models.HotSearch

	cacheKey := "hot_search"
	if err := utils.CacheGet(cacheKey, &hotSearches); err == nil {
		utils.Success(c, hotSearches)
		return
	}

	database.DB.Order("rank ASC").Limit(10).Find(&hotSearches)
	utils.CacheSet(cacheKey, hotSearches, 600e9) // 10 minutes
	utils.Success(c, hotSearches)
}

func GetSearchHistory(c *gin.Context) {
	userID := c.GetUint("user_id")
	var histories []models.SearchHistory
	database.DB.Where("user_id = ?", userID).Order("created_at DESC").Limit(20).Find(&histories)

	keywords := make([]string, 0, len(histories))
	for _, h := range histories {
		keywords = append(keywords, h.Keyword)
	}
	utils.Success(c, keywords)
}

func ClearSearchHistory(c *gin.Context) {
	userID := c.GetUint("user_id")
	database.DB.Where("user_id = ?", userID).Delete(&models.SearchHistory{})
	utils.Success(c, nil)
}

func GetSearchSuggest(c *gin.Context) {
	var dramas []models.Drama
	database.DB.Order("RAND()").Limit(6).Find(&dramas)
	utils.Success(c, dramas)
}
