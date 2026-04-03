package handlers

import (
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func GetDramas(c *gin.Context) {
	var dramas []models.Drama
	query := database.DB.Model(&models.Drama{}).Where("enabled = ?", true)

	if cat := c.Query("category"); cat != "" && cat != "推荐" {
		query = query.Where("category = ?", cat)
	}

	switch c.Query("sort") {
	case "hot":
		query = query.Order("heat DESC")
	case "rating":
		query = query.Order("rating DESC")
	default:
		query = query.Order("created_at DESC")
	}

	query.Find(&dramas)
	utils.Success(c, dramas)
}

func GetDramaDetail(c *gin.Context) {
	id := c.Param("id")
	var drama models.Drama

	cacheKey := "drama:" + id
	if err := utils.CacheGet(cacheKey, &drama); err == nil {
		if drama.Enabled {
			utils.Success(c, drama)
			return
		}
	}

	if err := database.DB.Where("id = ? AND enabled = ?", id, true).First(&drama).Error; err != nil {
		utils.BadRequest(c, "剧集不存在")
		return
	}

	utils.CacheSet(cacheKey, drama, 10*time.Minute)
	utils.Success(c, drama)
}

func GetHotDramas(c *gin.Context) {
	var dramas []models.Drama

	cacheKey := "hot_dramas"
	if err := utils.CacheGet(cacheKey, &dramas); err == nil {
		utils.Success(c, dramas)
		return
	}

	database.DB.Where("enabled = ?", true).Order("heat DESC").Limit(20).Find(&dramas)
	utils.CacheSet(cacheKey, dramas, 5*time.Minute)
	utils.Success(c, dramas)
}

func GetRecommendDramas(c *gin.Context) {
	var dramas []models.Drama
	database.DB.Where("enabled = ?", true).Order("rating DESC, heat DESC").Limit(10).Find(&dramas)
	utils.Success(c, dramas)
}

func GetCategories(c *gin.Context) {
	var categories []models.Category
	database.DB.Order("sort ASC").Find(&categories)
	names := make([]string, len(categories))
	for i, cat := range categories {
		names[i] = cat.Name
	}
	utils.Success(c, names)
}

func SearchDramas(c *gin.Context) {
	keyword := c.Query("keyword")
	if keyword == "" {
		utils.BadRequest(c, "请输入搜索关键词")
		return
	}
	var dramas []models.Drama
	database.DB.Where("enabled = ? AND (title LIKE ? OR tags LIKE ? OR description LIKE ?)",
		true, "%"+keyword+"%", "%"+keyword+"%", "%"+keyword+"%").
		Order("heat DESC").Limit(20).Find(&dramas)
	utils.Success(c, dramas)
}
