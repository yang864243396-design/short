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
	episodeNumber, _ := strconv.Atoi(c.DefaultQuery("episode_number", "1"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 50 {
		pageSize = 10
	}
	if episodeNumber < 1 {
		episodeNumber = 1
	}
	offset := (page - 1) * pageSize

	var dramas []models.Drama
	database.DB.Raw(`
		SELECT * FROM dramas
		WHERE enabled = 1 AND id IN (
			SELECT DISTINCT drama_id FROM episodes WHERE video_path IS NOT NULL AND video_path != ''
		)
		ORDER BY -LOG(RAND()) / GREATEST(heat, 1)
		LIMIT ? OFFSET ?
	`, pageSize, offset).Scan(&dramas)

	type feedRow struct {
		drama *models.Drama
		ep    models.Episode
	}
	rows := make([]feedRow, 0, len(dramas))
	for i := range dramas {
		drama := &dramas[i]
		var ep models.Episode
		result := database.DB.Where("drama_id = ? AND episode_number = ? AND video_path != ''", drama.ID, episodeNumber).First(&ep)
		if result.RowsAffected == 0 {
			database.DB.Where("drama_id = ? AND video_path != ''", drama.ID).Order("episode_number ASC").First(&ep)
			if ep.ID == 0 {
				continue
			}
		}
		rows = append(rows, feedRow{drama, ep})
	}

	items := make([]gin.H, 0, len(rows))
	for _, row := range rows {
		drama := row.drama
		ep := row.ep
		// 竖滑 Feed 不先走片头/Grant，须带短时 HMAC 才能直播；分集列表仍按权益收紧 stream_url
		streamURL := ""
		if ep.VideoPath != "" {
			streamURL = GenerateSignedStreamURL(ep.ID, ep.VideoPath)
		}

		AttachDramaRanking(drama)
		dramaH := gin.H{
			"id":             drama.ID,
			"title":          drama.Title,
			"cover_url":      drama.CoverURL,
			"description":    drama.Description,
			"category":        drama.Category,
			"category_list":   utils.ParseCategoryList(drama.Category),
			"total_episodes": drama.TotalEpisodes,
			"rating":         drama.Rating,
			"heat":           drama.Heat,
		}
		if drama.Ranking != nil {
			dramaH["ranking"] = gin.H{
				"list": drama.Ranking.List,
				"rank": drama.Ranking.Rank,
			}
		}

		items = append(items, gin.H{
			"id":             ep.ID,
			"drama_id":       ep.DramaID,
			"episode_number": ep.EpisodeNumber,
			"title":          ep.Title,
			"video_url":      ep.VideoURL,
			"stream_url":     streamURL,
			"is_free":        ep.IsFree,
			"like_count":     ep.LikeCount,
			"comment_count":  ep.CommentCount,
			"view_count":     ep.ViewCount,
			"drama":          dramaH,
		})
	}

	utils.Success(c, items)
}
