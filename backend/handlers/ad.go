package handlers

import (
	"math/rand"
	"strconv"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func GetAdVideo(c *gin.Context) {
	var ads []models.AdVideo
	database.DB.Where("enabled = ?", true).Find(&ads)

	if len(ads) == 0 {
		utils.BadRequest(c, "暂无广告")
		return
	}

	totalWeight := 0
	for _, ad := range ads {
		w := ad.Weight
		if w < 1 {
			w = 1
		}
		totalWeight += w
	}

	r := rand.Intn(totalWeight)
	cumulative := 0
	var selected models.AdVideo
	for _, ad := range ads {
		w := ad.Weight
		if w < 1 {
			w = 1
		}
		cumulative += w
		if r < cumulative {
			selected = ad
			break
		}
	}

	streamUrl := GenerateSignedStreamURL(selected.ID, selected.VideoPath)

	utils.Success(c, gin.H{
		"id":        selected.ID,
		"title":     selected.Title,
		"video_url": streamUrl,
		"duration":  selected.Duration,
	})
}

// --- Admin CRUD ---

func AdminGetAds(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "10"))

	var ads []models.AdVideo
	var total int64
	database.DB.Model(&models.AdVideo{}).Count(&total)
	database.DB.Order("id DESC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&ads)

	utils.Success(c, gin.H{"list": ads, "total": total, "page": page, "page_size": pageSize})
}

func AdminCreateAd(c *gin.Context) {
	var ad models.AdVideo
	if err := c.ShouldBindJSON(&ad); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	database.DB.Create(&ad)
	utils.Success(c, ad)
}

func AdminUpdateAd(c *gin.Context) {
	id := c.Param("id")
	var ad models.AdVideo
	if err := database.DB.First(&ad, id).Error; err != nil {
		utils.BadRequest(c, "广告不存在")
		return
	}
	if err := c.ShouldBindJSON(&ad); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	database.DB.Save(&ad)
	utils.Success(c, ad)
}

func AdminDeleteAd(c *gin.Context) {
	id := c.Param("id")
	database.DB.Delete(&models.AdVideo{}, id)
	utils.Success(c, nil)
}
