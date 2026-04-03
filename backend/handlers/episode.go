package handlers

import (
	"strconv"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func parseUint(s string) uint {
	v, _ := strconv.ParseUint(s, 10, 64)
	return uint(v)
}

func GetEpisodes(c *gin.Context) {
	dramaID := c.Param("id")
	var episodes []models.Episode
	database.DB.Where("drama_id = ?", dramaID).Order("episode_number ASC").Find(&episodes)

	type EpisodeResp struct {
		models.Episode
		StreamURL string `json:"stream_url"`
	}
	result := make([]EpisodeResp, len(episodes))
	for i, ep := range episodes {
		result[i] = EpisodeResp{Episode: ep}
		if ep.VideoPath != "" {
			result[i].StreamURL = GenerateSignedStreamURL(ep.ID, ep.VideoPath)
		}
	}
	utils.Success(c, result)
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
			"INSERT INTO drama_stats (drama_id, total_collects, updated_at) VALUES (?, 1, NOW()) "+
				"ON DUPLICATE KEY UPDATE total_collects = total_collects + 1, updated_at = NOW()", ep.DramaID)
	}()

	utils.Success(c, gin.H{"collected": true})
}
