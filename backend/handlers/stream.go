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

// StreamEpisodeMainGrantKey 经 GetAdVideo 片头/免广流程后、允许该用户对该集走无签拉流的短期授权（与客户端 10 分钟看广解一致，便于不再对未付费公下发 HMAC 直链）。
func StreamEpisodeMainGrantKey(userID, episodeID uint) string {
	return fmt.Sprintf("stream:epgrant:%d:%d", userID, episodeID)
}

// SetStreamEpisodeMainGrant 写入约 10 分钟有效主片拉流授权（与片头/免广同窗口）。
func SetStreamEpisodeMainGrant(userID, episodeID uint) {
	if userID == 0 || episodeID == 0 {
		return
	}
	_ = utils.Rdb.Set(utils.Ctx, StreamEpisodeMainGrantKey(userID, episodeID), "1", 10*time.Minute).Err()
}

func hasStreamEpisodeMainGrant(userID, episodeID uint) bool {
	if userID == 0 || episodeID == 0 {
		return false
	}
	n, err := utils.Rdb.Exists(utils.Ctx, StreamEpisodeMainGrantKey(userID, episodeID)).Result()
	return err == nil && n > 0
}

func streamSecret() string {
	return config.Load().JWT.Secret + ":stream"
}

func normalizePath(p string) string {
	return strings.ReplaceAll(p, "\\", "/")
}

func signedStreamTTLMinutes() int {
	m := config.Load().StreamSignedURLMinutes
	if m < 1 {
		return 30
	}
	return m
}

func GenerateSignedStreamURL(episodeID uint, videoPath string) string {
	expire := time.Now().Add(time.Duration(signedStreamTTLMinutes()) * time.Minute).Unix()
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

// serveStreamUploadFile 将 uploads 下已校验路径映射为本地磁盘路径并回传文件（分集与广告素材共用）。
func serveStreamUploadFile(c *gin.Context, diskRelativePath string) {
	if !isPathSafe(diskRelativePath) {
		log.Printf("[WARN] StreamVideo: suspicious path blocked: %s", diskRelativePath)
		c.Status(http.StatusForbidden)
		return
	}

	filePath := diskRelativePath
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

func StreamVideo(c *gin.Context) {
	episodeID := c.Param("episodeId")
	token := c.Query("token")
	expireStr := c.Query("expire")
	rawPath := c.Query("p")

	// 带 HMAC：路径 id 为「分集 ID」或「广告素材 ad_videos.id」；
	// GetAdVideo 对视频广告使用 AdVideo.ID 生成 `/api/v1/stream/{id}`，不得在验签前查询 episodes，否则 id 对不上分集直接 404。
	if token != "" && expireStr != "" && rawPath != "" {
		expire, err := strconv.ParseInt(expireStr, 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "bad expire"})
			return
		}
		decodedPath := normalizePath(rawPath)
		if !verifyStreamToken(episodeID, token, expire, decodedPath) {
			c.JSON(http.StatusForbidden, gin.H{"error": "invalid or expired stream token"})
			return
		}
		serveStreamUploadFile(c, decodedPath)
		return
	}

	var ep models.Episode
	if err := database.DB.First(&ep, episodeID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "episode not found"})
		return
	}
	var dramaRow models.Drama
	if err := database.DB.Select("enabled").Where("id = ?", ep.DramaID).First(&dramaRow).Error; err != nil || !dramaRow.Enabled {
		c.JSON(http.StatusForbidden, gin.H{"error": "content unavailable"})
		return
	}

	var videoPath string

	// 无 HMAC 签名时：须登录，且（免费集 或 已金币解锁本集）方可拉流
	userID := c.GetUint("user_id")
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"code": 401, "message": "请登录后播放，或使用带签名的播放地址"})
		return
	}
	if !ep.IsFree {
		var unlockCount int64
		database.DB.Model(&models.UserEpisodeCoinUnlock{}).
			Where("user_id = ? AND episode_id = ?", userID, ep.ID).Count(&unlockCount)
		if unlockCount < 1 && !hasStreamEpisodeMainGrant(userID, ep.ID) {
			c.JSON(http.StatusForbidden, gin.H{"code": 403, "message": "未解锁本集，请先走片头或免广后重试，或使用已签播放地址"})
			return
		}
	}
	videoPath = normalizePath(ep.VideoPath)
	if videoPath == "" {
		videoPath = fmt.Sprintf("./uploads/videos/%d/%d.mp4", ep.DramaID, ep.EpisodeNumber)
	}

	go func(dramaID uint, epID string) {
		viewKey := fmt.Sprintf("views:episode:%s", epID)
		utils.Rdb.Incr(utils.Ctx, viewKey)
		database.DB.Exec(
			"INSERT INTO drama_stats (drama_id, total_views, updated_at) VALUES (?, 1, NOW()) "+
				"ON DUPLICATE KEY UPDATE total_views = total_views + 1, updated_at = NOW()", dramaID)
	}(ep.DramaID, episodeID)

	serveStreamUploadFile(c, videoPath)
}
