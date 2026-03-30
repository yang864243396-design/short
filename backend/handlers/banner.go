package handlers

import (
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func GetActiveBanners(c *gin.Context) {
	var banners []models.Banner
	cacheKey := "banners:active"

	if err := utils.CacheGet(cacheKey, &banners); err == nil {
		utils.Success(c, banners)
		return
	}

	now := time.Now()
	database.DB.Where("status = ? AND (start_time IS NULL OR start_time <= ?) AND (end_time IS NULL OR end_time >= ?)",
		1, now, now).
		Order("sort ASC, id DESC").
		Find(&banners)

	utils.CacheSet(cacheKey, banners, 120e9) // 2 minutes
	utils.Success(c, banners)
}

// --- Admin CRUD ---

func AdminGetBanners(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))

	var banners []models.Banner
	var total int64
	database.DB.Model(&models.Banner{}).Count(&total)
	database.DB.Order("sort ASC, id DESC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&banners)

	utils.Success(c, gin.H{"list": banners, "total": total, "page": page, "page_size": pageSize})
}

func AdminCreateBanner(c *gin.Context) {
	var banner models.Banner
	if err := c.ShouldBindJSON(&banner); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	database.DB.Create(&banner)
	utils.CacheDelete("banners:active")
	utils.Success(c, banner)
}

func AdminUpdateBanner(c *gin.Context) {
	id := c.Param("id")
	var banner models.Banner
	if err := database.DB.First(&banner, id).Error; err != nil {
		utils.BadRequest(c, "广告不存在")
		return
	}
	if err := c.ShouldBindJSON(&banner); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	database.DB.Save(&banner)
	utils.CacheDelete("banners:active")
	utils.Success(c, banner)
}

func AdminDeleteBanner(c *gin.Context) {
	id := c.Param("id")
	database.DB.Delete(&models.Banner{}, id)
	utils.CacheDelete("banners:active")
	utils.Success(c, nil)
}
