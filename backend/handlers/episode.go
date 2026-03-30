package handlers

import (
	"strconv"

	"github.com/gin-gonic/gin"
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
	utils.Success(c, episodes)
}

func LikeEpisode(c *gin.Context) {
	userID := c.GetUint("user_id")
	episodeID := c.Param("id")

	var existing models.UserLike
	result := database.DB.Where("user_id = ? AND episode_id = ?", userID, episodeID).First(&existing)

	if result.RowsAffected > 0 {
		database.DB.Delete(&existing)
		utils.Success(c, gin.H{"liked": false})
		return
	}

	like := models.UserLike{UserID: userID, EpisodeID: parseUint(episodeID)}
	database.DB.Create(&like)

	var ep models.Episode
	if database.DB.First(&ep, episodeID).Error == nil {
		go func(dramaID uint) {
			database.DB.Exec(
				"INSERT INTO drama_stats (drama_id, total_likes, updated_at) VALUES (?, 1, NOW()) "+
					"ON DUPLICATE KEY UPDATE total_likes = total_likes + 1, updated_at = NOW()", dramaID)
		}(ep.DramaID)
	}

	utils.Success(c, gin.H{"liked": true})
}

func CollectEpisode(c *gin.Context) {
	userID := c.GetUint("user_id")
	episodeID := c.Param("id")

	var episode models.Episode
	if err := database.DB.First(&episode, episodeID).Error; err != nil {
		utils.BadRequest(c, "剧集不存在")
		return
	}

	var existing models.UserCollect
	result := database.DB.Where("user_id = ? AND drama_id = ?", userID, episode.DramaID).First(&existing)

	if result.RowsAffected > 0 {
		database.DB.Delete(&existing)
		utils.Success(c, gin.H{"collected": false})
		return
	}

	collect := models.UserCollect{UserID: userID, DramaID: episode.DramaID}
	database.DB.Create(&collect)

	go func(dramaID uint) {
		database.DB.Exec(
			"INSERT INTO drama_stats (drama_id, total_collects, updated_at) VALUES (?, 1, NOW()) "+
				"ON DUPLICATE KEY UPDATE total_collects = total_collects + 1, updated_at = NOW()", dramaID)
	}(episode.DramaID)

	utils.Success(c, gin.H{"collected": true})
}
