package handlers

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func StreamVideo(c *gin.Context) {
	episodeID := c.Param("episodeId")

	var episode models.Episode
	if err := database.DB.First(&episode, episodeID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "episode not found"})
		return
	}

	// Increment view count via Redis (dedup per request burst)
	viewKey := fmt.Sprintf("views:episode:%s", episodeID)
	utils.Rdb.Incr(utils.Ctx, viewKey)

	// Increment drama-level stats (async, non-blocking)
	go func(dramaID uint) {
		database.DB.Exec(
			"INSERT INTO drama_stats (drama_id, total_views, updated_at) VALUES (?, 1, NOW()) "+
				"ON DUPLICATE KEY UPDATE total_views = total_views + 1, updated_at = NOW()",
			dramaID)

		today := fmt.Sprintf("%s", strings.Split(fmt.Sprintf("%v", database.DB.NowFunc()), " ")[0])
		database.DB.Exec(
			"INSERT INTO daily_snapshots (drama_id, date, views, created_at) VALUES (?, ?, 1, NOW()) "+
				"ON DUPLICATE KEY UPDATE views = views + 1",
			dramaID, today)
	}(episode.DramaID)

	videoPath := episode.VideoPath
	if videoPath == "" {
		videoPath = fmt.Sprintf("./videos/%d/%d.mp4", episode.DramaID, episode.EpisodeNumber)
	}

	file, err := os.Open(videoPath)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "video file not found"})
		return
	}
	defer file.Close()

	stat, err := file.Stat()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "cannot read file info"})
		return
	}

	fileSize := stat.Size()
	rangeHeader := c.GetHeader("Range")

	if rangeHeader == "" {
		c.Header("Content-Type", "video/mp4")
		c.Header("Content-Length", strconv.FormatInt(fileSize, 10))
		c.Header("Accept-Ranges", "bytes")
		c.Status(http.StatusOK)
		io.Copy(c.Writer, file)
		return
	}

	rangeParts := strings.Replace(rangeHeader, "bytes=", "", 1)
	rangeSplit := strings.Split(rangeParts, "-")

	start, _ := strconv.ParseInt(rangeSplit[0], 10, 64)
	var end int64
	if len(rangeSplit) > 1 && rangeSplit[1] != "" {
		end, _ = strconv.ParseInt(rangeSplit[1], 10, 64)
	} else {
		end = fileSize - 1
	}

	chunkSize := end - start + 1

	c.Header("Content-Type", "video/mp4")
	c.Header("Content-Range", fmt.Sprintf("bytes %d-%d/%d", start, end, fileSize))
	c.Header("Content-Length", strconv.FormatInt(chunkSize, 10))
	c.Header("Accept-Ranges", "bytes")
	c.Header("Cache-Control", "public, max-age=86400")
	c.Status(http.StatusPartialContent)

	file.Seek(start, io.SeekStart)
	io.CopyN(c.Writer, file, chunkSize)
}
