package handlers

import (
	"strconv"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func GetFeed(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "10"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 50 {
		pageSize = 10
	}
	offset := (page - 1) * pageSize

	var episodes []models.Episode
	database.DB.Preload("Drama").
		Joins("LEFT JOIN dramas ON dramas.id = episodes.drama_id").
		Where("episodes.is_free = ?", true).
		Order("RAND()").
		Offset(offset).Limit(pageSize).
		Find(&episodes)

	type FeedItem struct {
		models.Episode
		Drama *models.Drama `json:"drama"`
	}

	items := make([]gin.H, 0, len(episodes))
	for _, ep := range episodes {
		var drama models.Drama
		database.DB.First(&drama, ep.DramaID)
		items = append(items, gin.H{
			"id":             ep.ID,
			"drama_id":       ep.DramaID,
			"episode_number": ep.EpisodeNumber,
			"title":          ep.Title,
			"video_url":      ep.VideoURL,
			"duration":       ep.Duration,
			"is_free":        ep.IsFree,
			"like_count":     ep.LikeCount,
			"comment_count":  ep.CommentCount,
			"view_count":     ep.ViewCount,
			"drama": gin.H{
				"id":             drama.ID,
				"title":          drama.Title,
				"cover_url":      drama.CoverURL,
				"description":    drama.Description,
				"category":       drama.Category,
				"total_episodes": drama.TotalEpisodes,
				"rating":         drama.Rating,
				"heat":           drama.Heat,
			},
		})
	}

	utils.Success(c, items)
}
