package handlers

import (
	"strconv"
	"strings"
	"time"

	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"

	"github.com/gin-gonic/gin"
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

	banners = filterBannersSkipOffShelfDramas(banners)

	utils.CacheSet(cacheKey, banners, 120e9) // 2 minutes
	utils.Success(c, banners)
}

// --- Admin CRUD ---

func AdminGetBanners(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 {
		pageSize = 20
	}

	keyword := strings.TrimSpace(c.Query("keyword"))
	linkType := strings.TrimSpace(c.Query("link_type"))
	statusStr := strings.ToLower(strings.TrimSpace(c.Query("status")))

	query := database.DB.Model(&models.Banner{})
	if keyword != "" {
		kw := "%" + keyword + "%"
		if id, err := strconv.ParseUint(keyword, 10, 64); err == nil && id > 0 {
			query = query.Where("id = ? OR title LIKE ? OR link_url LIKE ? OR drama_id = ?",
				uint(id), kw, kw, uint(id))
		} else {
			query = query.Where("title LIKE ? OR link_url LIKE ?", kw, kw)
		}
	}
	if linkType == "url" || linkType == "drama" {
		query = query.Where("link_type = ?", linkType)
	}
	switch statusStr {
	case "1", "true", "yes":
		query = query.Where("status = ?", 1)
	case "0", "false", "no":
		query = query.Where("status = ?", 0)
	}

	var total int64
	query.Count(&total)

	var banners []models.Banner
	query.Order("sort ASC, id DESC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&banners)

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

// 下架剧集不在客户端展示：轮播跳转剧目时过滤
func filterBannersSkipOffShelfDramas(banners []models.Banner) []models.Banner {
	if len(banners) == 0 {
		return banners
	}
	out := make([]models.Banner, 0, len(banners))
	for i := range banners {
		b := banners[i]
		if strings.EqualFold(b.LinkType, "drama") && b.DramaID > 0 {
			var d models.Drama
			if err := database.DB.Select("enabled").Where("id = ?", b.DramaID).First(&d).Error; err != nil || !d.Enabled {
				continue
			}
		}
		out = append(out, b)
	}
	return out
}
