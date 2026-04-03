package handlers

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func streamSecret() string {
	return config.Load().JWT.Secret + ":stream"
}

func normalizePath(p string) string {
	return strings.ReplaceAll(p, "\\", "/")
}

func GenerateSignedStreamURL(episodeID uint, videoPath string) string {
	expire := time.Now().Add(2 * time.Hour).Unix()
	normalizedPath := normalizePath(videoPath)

	msg := fmt.Sprintf("%d:%d:%s", episodeID, expire, normalizedPath)
	mac := hmac.New(sha256.New, []byte(streamSecret()))
	mac.Write([]byte(msg))
	token := base64.RawURLEncoding.EncodeToString(mac.Sum(nil))

	encodedPath := url.QueryEscape(normalizedPath)
	return fmt.Sprintf("/api/v1/stream/%d?token=%s&expire=%d&p=%s",
		episodeID, token, expire, encodedPath)
}

func verifyStreamToken(episodeID string, token string, expire int64, videoPath string) bool {
	if time.Now().Unix() > expire {
		return false
	}
	msg := fmt.Sprintf("%s:%d:%s", episodeID, expire, videoPath)
	mac := hmac.New(sha256.New, []byte(streamSecret()))
	mac.Write([]byte(msg))
	expected := base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
	return hmac.Equal([]byte(token), []byte(expected))
}

func isPathSafe(videoPath string) bool {
	if strings.Contains(videoPath, "..") {
		return false
	}
	return strings.HasPrefix(videoPath, "./uploads/") ||
		strings.HasPrefix(videoPath, "uploads/") ||
		strings.HasPrefix(videoPath, "/uploads/")
}

func StreamVideo(c *gin.Context) {
	episodeID := c.Param("episodeId")
	token := c.Query("token")
	expireStr := c.Query("expire")
	rawPath := c.Query("p")

	var videoPath string

	if token != "" && expireStr != "" && rawPath != "" {
		expire, _ := strconv.ParseInt(expireStr, 10, 64)
		decodedPath := normalizePath(rawPath)
		if !verifyStreamToken(episodeID, token, expire, decodedPath) {
			c.JSON(http.StatusForbidden, gin.H{"error": "invalid or expired stream token"})
			return
		}
		videoPath = decodedPath
	} else {
		var episode models.Episode
		if err := database.DB.First(&episode, episodeID).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "episode not found"})
			return
		}
		videoPath = normalizePath(episode.VideoPath)
		if videoPath == "" {
			videoPath = fmt.Sprintf("./uploads/videos/%d/%d.mp4", episode.DramaID, episode.EpisodeNumber)
		}

		go func(dramaID uint, epID string) {
			viewKey := fmt.Sprintf("views:episode:%s", epID)
			utils.Rdb.Incr(utils.Ctx, viewKey)
			database.DB.Exec(
				"INSERT INTO drama_stats (drama_id, total_views, updated_at) VALUES (?, 1, NOW()) "+
					"ON DUPLICATE KEY UPDATE total_views = total_views + 1, updated_at = NOW()", dramaID)
		}(episode.DramaID, episodeID)
	}

	if !isPathSafe(videoPath) {
		log.Printf("[WARN] StreamVideo: suspicious path blocked: %s", videoPath)
		c.Status(http.StatusForbidden)
		return
	}

	filePath := videoPath
	if !strings.HasPrefix(filePath, ".") && !strings.HasPrefix(filePath, "/") {
		filePath = "./" + filePath
	} else if strings.HasPrefix(filePath, "/") {
		filePath = "." + filePath
	}

	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		c.JSON(http.StatusNotFound, gin.H{"error": "video file not found"})
		return
	}

	c.Header("Cache-Control", "public, max-age=86400")
	c.File(filePath)
}
