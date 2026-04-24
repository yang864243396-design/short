package handlers

import (
	"errors"
	"fmt"
	"strconv"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
	"short-drama-backend/wallet"
)

func parseUint(s string) uint {
	v, _ := strconv.ParseUint(s, 10, 64)
	return uint(v)
}

func GetEpisodes(c *gin.Context) {
	dramaID := c.Param("id")
	did := parseUint(dramaID)
	if did == 0 {
		utils.BadRequest(c, "无效剧目")
		return
	}
	var drama models.Drama
	if err := database.DB.Select("id", "enabled").Where("id = ? AND enabled = ?", did, true).First(&drama).Error; err != nil {
		utils.BadRequest(c, "剧集不存在")
		return
	}
	var episodes []models.Episode
	// App 端只返回已上传视频的分集，避免选集里出现「无片可播」的占位集
	database.DB.Where("drama_id = ?", did).
		Where("video_path IS NOT NULL AND video_path != ''").
		Order("episode_number ASC").
		Find(&episodes)

	userID := c.GetUint("user_id")
	coinUnlocked := map[uint]bool{}
	if userID > 0 && len(episodes) > 0 {
		ids := make([]uint, 0, len(episodes))
		for _, ep := range episodes {
			ids = append(ids, ep.ID)
		}
		var rows []models.UserEpisodeCoinUnlock
		database.DB.Select("episode_id").Where("user_id = ? AND episode_id IN ?", userID, ids).Find(&rows)
		for _, r := range rows {
			coinUnlocked[r.EpisodeID] = true
		}
	}

	type EpisodeResp struct {
		models.Episode
		StreamURL    string `json:"stream_url"`
		CoinUnlocked bool   `json:"coin_unlocked"`
	}
	result := make([]EpisodeResp, len(episodes))
	for i, ep := range episodes {
		result[i] = EpisodeResp{Episode: ep, CoinUnlocked: coinUnlocked[ep.ID]}
		if ep.VideoPath != "" {
			// 付费且未永久解锁：不下发可转发的 HMAC 直链；走无签拉流需配合 GetAdVideo 写入的短时授权
			if ep.IsFree || (userID > 0 && coinUnlocked[ep.ID]) {
				result[i].StreamURL = GenerateSignedStreamURL(ep.ID, ep.VideoPath)
			}
		}
	}
	utils.Success(c, result)
}

// UnlockEpisodeWithCoins 支付金币永久解锁本集（免广告），幂等；扣款写入钱包流水。
func UnlockEpisodeWithCoins(c *gin.Context) {
	userID := c.GetUint("user_id")
	episodeID := parseUint(c.Param("id"))
	if episodeID == 0 {
		utils.BadRequest(c, "无效分集")
		return
	}
	var ep models.Episode
	if err := database.DB.First(&ep, episodeID).Error; err != nil {
		utils.BadRequest(c, "分集不存在")
		return
	}
	if ep.IsFree {
		utils.BadRequest(c, "免费集无需解锁")
		return
	}
	if ep.UnlockCoins < 1 {
		utils.BadRequest(c, "该剧集未开放金币解锁")
		return
	}
	var drama models.Drama
	if err := database.DB.Select("id", "title", "enabled").First(&drama, ep.DramaID).Error; err != nil || !drama.Enabled {
		utils.BadRequest(c, "剧集不可用")
		return
	}

	var existing models.UserEpisodeCoinUnlock
	if err := database.DB.Where("user_id = ? AND episode_id = ?", userID, episodeID).First(&existing).Error; err == nil {
		var u models.AppUser
		database.DB.Select("coins").Where("id = ? AND deleted_at IS NULL", userID).First(&u)
		utils.Success(c, gin.H{"coin_unlocked": true, "balance_after": u.Coins})
		return
	}

	title := "分集永久解锁"
	remark := fmt.Sprintf("解锁《%s》第%d集（免广告）", drama.Title, ep.EpisodeNumber)

	var balanceAfter int
	err := database.DB.Transaction(func(tx *gorm.DB) error {
		var err error
		balanceAfter, err = wallet.Apply(tx, userID, models.WalletTxConsume, ep.UnlockCoins,
			title, remark, models.WalletRefEpisodeUnlock, episodeID, 0, "")
		if err != nil {
			return err
		}
		return tx.Create(&models.UserEpisodeCoinUnlock{UserID: userID, EpisodeID: episodeID}).Error
	})
	if err != nil {
		if errors.Is(err, wallet.ErrInsufficient) {
			utils.BadRequest(c, "金币不足")
			return
		}
		if errors.Is(err, wallet.ErrAccountDeleted) {
			utils.Unauthorized(c)
			return
		}
		utils.ServerError(c, "解锁失败")
		return
	}
	utils.Success(c, gin.H{"coin_unlocked": true, "balance_after": balanceAfter})
}

func addDramaHeat(dramaID uint, amount int64) {
	database.DB.Model(&models.Drama{}).Where("id = ?", dramaID).
		UpdateColumn("heat", gorm.Expr("heat + ?", amount))
}

func LikeEpisode(c *gin.Context) {
	userID := c.GetUint("user_id")
	episodeID := parseUint(c.Param("id"))

	var ep models.Episode
	if database.DB.First(&ep, episodeID).Error != nil {
		utils.BadRequest(c, "剧集不存在")
		return
	}

	var existing models.UserLike
	result := database.DB.Where("user_id = ? AND episode_id = ?", userID, episodeID).First(&existing)

	if result.RowsAffected > 0 {
		if existing.Active {
			database.DB.Model(&existing).Update("active", false)
			database.DB.Model(&models.Episode{}).Where("id = ?", episodeID).
				UpdateColumn("like_count", gorm.Expr("GREATEST(like_count - 1, 0)"))
			go func() {
				addDramaHeat(ep.DramaID, -1)
				database.DB.Exec(
					"UPDATE drama_stats SET total_likes = GREATEST(total_likes - 1, 0), updated_at = NOW() WHERE drama_id = ?", ep.DramaID)
			}()
			utils.Success(c, gin.H{"liked": false})
		} else {
			database.DB.Model(&existing).Update("active", true)
			database.DB.Model(&models.Episode{}).Where("id = ?", episodeID).
				UpdateColumn("like_count", gorm.Expr("like_count + 1"))
			go func() {
				addDramaHeat(ep.DramaID, 1)
				database.DB.Exec(
					"UPDATE drama_stats SET total_likes = total_likes + 1, updated_at = NOW() WHERE drama_id = ?", ep.DramaID)
			}()
			utils.Success(c, gin.H{"liked": true})
		}
		return
	}

	like := models.UserLike{
		UserID:    userID,
		EpisodeID: episodeID,
		DramaID:   ep.DramaID,
		Active:    true,
		HeatAdded: true,
	}
	database.DB.Create(&like)

	database.DB.Model(&models.Episode{}).Where("id = ?", episodeID).
		UpdateColumn("like_count", gorm.Expr("like_count + 1"))

	go func() {
		addDramaHeat(ep.DramaID, 1)
		database.DB.Exec(
			"INSERT INTO drama_stats (drama_id, total_likes, updated_at) VALUES (?, 1, NOW()) "+
				"ON DUPLICATE KEY UPDATE total_likes = total_likes + 1, updated_at = NOW()", ep.DramaID)
	}()

	utils.Success(c, gin.H{"liked": true})
}

func GetEpisodeInteraction(c *gin.Context) {
	userID := c.GetUint("user_id")
	episodeID := parseUint(c.Param("id"))

	var ep models.Episode
	if database.DB.First(&ep, episodeID).Error != nil {
		utils.BadRequest(c, "剧集不存在")
		return
	}

	var likeCount int64
	database.DB.Model(&models.UserLike{}).Where("user_id = ? AND episode_id = ? AND active = ?", userID, episodeID, true).Count(&likeCount)
	liked := likeCount > 0

	var collectCount int64
	database.DB.Model(&models.UserCollect{}).Where("user_id = ? AND drama_id = ? AND active = ?", userID, ep.DramaID, true).Count(&collectCount)
	collected := collectCount > 0

	utils.Success(c, gin.H{"liked": liked, "collected": collected})
}

func RecordHistory(c *gin.Context) {
	userID := c.GetUint("user_id")
	episodeID := parseUint(c.Param("id"))

	var ep models.Episode
	if database.DB.First(&ep, episodeID).Error != nil {
		utils.BadRequest(c, "剧集不存在")
		return
	}

	var existing models.UserHistory
	result := database.DB.Where("user_id = ? AND drama_id = ?", userID, ep.DramaID).First(&existing)

	if result.RowsAffected > 0 {
		database.DB.Model(&existing).Updates(map[string]interface{}{
			"episode_id": episodeID,
			"progress":   ep.EpisodeNumber,
		})
	} else {
		history := models.UserHistory{
			UserID:    userID,
			DramaID:   ep.DramaID,
			EpisodeID: episodeID,
			Progress:  ep.EpisodeNumber,
		}
		database.DB.Create(&history)
	}

	go func() {
		addDramaHeat(ep.DramaID, 1)
		database.DB.Exec(
			"INSERT INTO drama_stats (drama_id, total_views, updated_at) VALUES (?, 1, NOW()) "+
				"ON DUPLICATE KEY UPDATE total_views = total_views + 1, updated_at = NOW()", ep.DramaID)
	}()

	utils.Success(c, nil)
}

func CollectEpisode(c *gin.Context) {
	userID := c.GetUint("user_id")
	episodeID := parseUint(c.Param("id"))

	var ep models.Episode
	if database.DB.First(&ep, episodeID).Error != nil {
		utils.BadRequest(c, "剧集不存在")
		return
	}

	var existing models.UserCollect
	result := database.DB.Where("user_id = ? AND drama_id = ?", userID, ep.DramaID).First(&existing)

	if result.RowsAffected > 0 {
		if existing.Active {
			database.DB.Model(&existing).Update("active", false)
			utils.Success(c, gin.H{"collected": false})
		} else {
			database.DB.Model(&existing).Update("active", true)
			utils.Success(c, gin.H{"collected": true})
		}
		return
	}

	collect := models.UserCollect{
		UserID:    userID,
		DramaID:   ep.DramaID,
		Active:    true,
		HeatAdded: true,
	}
	database.DB.Create(&collect)

	go func() {
		addDramaHeat(ep.DramaID, 100)
		database.DB.Exec(
			"INSERT INTO drama_stats (drama_id, total_collects, heat_from_collects, updated_at) VALUES (?, 1, 100, NOW()) "+
				"ON DUPLICATE KEY UPDATE total_collects = total_collects + 1, heat_from_collects = heat_from_collects + 100, updated_at = NOW()", ep.DramaID)
	}()

	utils.Success(c, gin.H{"collected": true})
}
