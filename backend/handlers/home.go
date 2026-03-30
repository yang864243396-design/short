package handlers

import (
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func GetHomePage(c *gin.Context) {
	cacheKey := "home:page"
	var cached gin.H
	if err := utils.CacheGet(cacheKey, &cached); err == nil {
		utils.Success(c, cached)
		return
	}

	var categories []models.Category
	database.DB.Order("sort ASC").Find(&categories)

	var mustWatch []models.Drama
	database.DB.Where("rating >= ?", 9.0).Order("heat DESC").Limit(10).Find(&mustWatch)

	var recommend []models.Drama
	database.DB.Order("updated_at DESC").Limit(10).Find(&recommend)

	hotRanking := GetHomeHotRankingFromCache(10)

	data := gin.H{
		"categories":  categories,
		"must_watch":  mustWatch,
		"recommend":   recommend,
		"hot_ranking": hotRanking,
	}

	_ = utils.CacheSet(cacheKey, data, 3*time.Minute)
	utils.Success(c, data)
}
