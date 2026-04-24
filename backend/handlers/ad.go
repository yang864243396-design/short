package handlers

import (
	"fmt"
	"math/rand"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func adIsPlayable(ad models.AdVideo) bool {
	mt := strings.ToLower(strings.TrimSpace(ad.MediaType))
	if mt == "" || mt == "video" {
		return strings.TrimSpace(ad.VideoPath) != ""
	}
	if mt == "image" {
		return strings.TrimSpace(ad.ImageURL) != ""
	}
	return false
}

// normalizeAdVideoForSave 按 media_type 互斥落库，避免视频/图片路径同时存在时客户端误判
func normalizeAdVideoForSave(ad *models.AdVideo) {
	mt := strings.ToLower(strings.TrimSpace(ad.MediaType))
	if mt == "image" {
		ad.MediaType = "image"
		ad.VideoPath = ""
		return
	}
	ad.MediaType = "video"
	ad.ImageURL = ""
}

func adSkipEpisodeCooldownKey(userID uint, episodeID uint) string {
	return fmt.Sprintf("adskip:uct:%d:e:%d", userID, episodeID)
}

// adSkipInSameEpisodeCooldown 本集在首次扣次免广后 10 分钟内再次进片头：免广、不再次扣次、不下发广告素材（与 §3.5 同集冷却一致）。
func adSkipInSameEpisodeCooldown(userID uint, episodeID uint) bool {
	if userID == 0 || episodeID == 0 {
		return false
	}
	n, err := utils.Rdb.Exists(utils.Ctx, adSkipEpisodeCooldownKey(userID, episodeID)).Result()
	return err == nil && n > 0
}

// tryAdSkipWithConsume 片头前调用：在有效期内、剩余次数>0、同集 10 分钟未扣过次 时扣 1 次并免广。
// 同集冷却用 Redis SETNX，与 MySQL 扣次顺序：先占坑再扣次，失败则删 key。
func tryAdSkipWithConsume(userID uint, episodeID uint) bool {
	if userID == 0 || episodeID == 0 {
		return false
	}
	key := adSkipEpisodeCooldownKey(userID, episodeID)
	ok, rerr := utils.Rdb.SetNX(utils.Ctx, key, "1", 10*time.Minute).Result()
	if rerr != nil || !ok {
		return false
	}
	now := time.Now()
	res := database.DB.Model(&models.AppUser{}).
		Where("id = ? AND deleted_at IS NULL AND ad_skip_expires_at > ? AND ad_skip_remaining > 0", userID, now).
		UpdateColumn("ad_skip_remaining", gorm.Expr("ad_skip_remaining - 1"))
	if res.Error != nil || res.RowsAffected == 0 {
		_, _ = utils.Rdb.Del(utils.Ctx, key).Result()
		if res.Error == nil && res.RowsAffected == 0 {
			var u models.AppUser
			if database.DB.Select("ad_skip_expires_at", "ad_skip_remaining").Where("id = ? AND deleted_at IS NULL", userID).First(&u).Error == nil {
				if u.AdSkipExpiresAt != nil && !u.AdSkipExpiresAt.After(now) && u.AdSkipRemaining > 0 {
					_ = database.DB.Model(&models.AppUser{}).Where("id = ?", userID).Update("ad_skip_remaining", 0).Error
				}
			}
		}
		return false
	}
	return true
}

func GetAdVideo(c *gin.Context) {
	episodeQ := strings.TrimSpace(c.Query("episode_id"))
	var epID uint
	if episodeQ != "" {
		if v, e := strconv.ParseUint(episodeQ, 10, 64); e == nil && v > 0 {
			epID = uint(v)
		}
	}
	if uid := c.GetUint("user_id"); uid > 0 && epID > 0 {
		if adSkipInSameEpisodeCooldown(uid, epID) {
			SetStreamEpisodeMainGrant(uid, epID)
			utils.Success(c, gin.H{"skip_ad": true})
			return
		}
		if tryAdSkipWithConsume(uid, epID) {
			SetStreamEpisodeMainGrant(uid, epID)
			utils.Success(c, gin.H{"skip_ad": true})
			return
		}
	}

	var ads []models.AdVideo
	database.DB.Where("enabled = ?", true).Find(&ads)

	var playable []models.AdVideo
	for _, ad := range ads {
		if adIsPlayable(ad) {
			playable = append(playable, ad)
		}
	}
	ads = playable

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

	// 客户端必须以 media_type 为准选择展示方式；与 DB 是否曾存过另一类型素材无关
	mt := strings.ToLower(strings.TrimSpace(selected.MediaType))
	if mt == "image" {
		if uid := c.GetUint("user_id"); uid > 0 && epID > 0 {
			SetStreamEpisodeMainGrant(uid, epID)
		}
		out := gin.H{
			"id":         selected.ID,
			"title":      selected.Title,
			"duration":   selected.Duration,
			"media_type": "image",
			"image_url":  strings.TrimSpace(selected.ImageURL),
			"video_url":  "",
		}
		utils.Success(c, out)
		return
	}
	mt = "video"
	if uid := c.GetUint("user_id"); uid > 0 && epID > 0 {
		SetStreamEpisodeMainGrant(uid, epID)
	}
	out := gin.H{
		"id":         selected.ID,
		"title":      selected.Title,
		"duration":   selected.Duration,
		"media_type": mt,
		"video_url":  GenerateSignedStreamURL(selected.ID, selected.VideoPath),
		"image_url":  "",
	}

	utils.Success(c, out)
}

// --- Admin CRUD ---

// adminAdVideoFilteredQuery 列表与统计共用条件；Count 与 Find 各建一次查询，避免 GORM Count 影响后续链式查询。
func adminAdVideoFilteredQuery(keyword, mediaType, enabledStr string) *gorm.DB {
	q := database.DB.Model(&models.AdVideo{})
	kwRaw := strings.TrimSpace(keyword)
	if kwRaw != "" {
		kw := "%" + kwRaw + "%"
		if id, err := strconv.ParseUint(kwRaw, 10, 64); err == nil && id > 0 {
			q = q.Where("id = ? OR title LIKE ?", uint(id), kw)
		} else {
			q = q.Where("title LIKE ?", kw)
		}
	}
	mt := strings.ToLower(strings.TrimSpace(mediaType))
	if mt == "image" {
		q = q.Where("LOWER(TRIM(media_type)) = ?", "image")
	} else if mt == "video" {
		q = q.Where("(media_type IS NULL OR TRIM(media_type) = '' OR LOWER(TRIM(media_type)) = ?)", "video")
	}
	switch strings.ToLower(strings.TrimSpace(enabledStr)) {
	case "1", "true", "yes":
		q = q.Where("enabled = ?", true)
	case "0", "false", "no":
		q = q.Where("enabled = ?", false)
	}
	return q
}

func AdminGetAds(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "10"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 {
		pageSize = 10
	}

	keyword := strings.TrimSpace(c.Query("keyword"))
	mediaType := strings.TrimSpace(c.Query("media_type"))
	enabledStr := strings.TrimSpace(c.Query("enabled"))

	var total int64
	adminAdVideoFilteredQuery(keyword, mediaType, enabledStr).Count(&total)

	var ads []models.AdVideo
	adminAdVideoFilteredQuery(keyword, mediaType, enabledStr).
		Order("id DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&ads)

	utils.Success(c, gin.H{"list": ads, "total": total, "page": page, "page_size": pageSize})
}

func AdminCreateAd(c *gin.Context) {
	var ad models.AdVideo
	if err := c.ShouldBindJSON(&ad); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	normalizeAdVideoForSave(&ad)
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
	normalizeAdVideoForSave(&ad)
	database.DB.Save(&ad)
	utils.Success(c, ad)
}

func AdminDeleteAd(c *gin.Context) {
	id := c.Param("id")
	database.DB.Delete(&models.AdVideo{}, id)
	utils.Success(c, nil)
}
