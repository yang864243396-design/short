package handlers

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func GetProfile(c *gin.Context) {
	userID := c.GetUint("user_id")
	var user models.AppUser
	if err := database.DB.First(&user, userID).Error; err != nil {
		utils.BadRequest(c, "用户不存在")
		return
	}
	utils.Success(c, user)
}

func UpdateProfile(c *gin.Context) {
	userID := c.GetUint("user_id")
	var req struct {
		Nickname string `json:"nickname"`
		Avatar   string `json:"avatar"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}

	updates := map[string]interface{}{}
	if req.Nickname != "" {
		updates["nickname"] = req.Nickname
	}
	if req.Avatar != "" {
		updates["avatar"] = req.Avatar
	}

	database.DB.Model(&models.AppUser{}).Where("id = ?", userID).Updates(updates)

	var user models.AppUser
	database.DB.First(&user, userID)
	utils.Success(c, user)
}

func UploadAvatar(c *gin.Context) {
	userID := c.GetUint("user_id")

	file, header, err := c.Request.FormFile("file")
	if err != nil {
		utils.BadRequest(c, "请选择图片")
		return
	}
	defer file.Close()

	uploadDir := "./uploads/avatars"
	os.MkdirAll(uploadDir, os.ModePerm)

	filename := fmt.Sprintf("%d_%d_%s", userID, time.Now().UnixMilli(), header.Filename)
	dst := filepath.Join(uploadDir, filename)

	out, err := os.Create(dst)
	if err != nil {
		utils.ServerError(c, "保存文件失败")
		return
	}
	defer out.Close()
	io.Copy(out, file)

	avatarURL := "/uploads/avatars/" + filename
	database.DB.Model(&models.AppUser{}).Where("id = ?", userID).Update("avatar", avatarURL)

	var user models.AppUser
	database.DB.First(&user, userID)
	utils.Success(c, user)
}

func GetHistory(c *gin.Context) {
	userID := c.GetUint("user_id")
	var histories []models.UserHistory
	database.DB.Where("user_id = ?", userID).Order("updated_at DESC").Limit(50).Find(&histories)

	if len(histories) == 0 {
		utils.Success(c, []struct{}{})
		return
	}

	dramaIDs := make([]uint, len(histories))
	for i, h := range histories {
		dramaIDs[i] = h.DramaID
	}
	var dramas []models.Drama
	database.DB.Where("id IN ?", dramaIDs).Find(&dramas)
	dramaMap := map[uint]*models.Drama{}
	for i := range dramas {
		dramaMap[dramas[i].ID] = &dramas[i]
	}

	type HistoryItem struct {
		Drama       *models.Drama `json:"drama"`
		LastEpisode int           `json:"last_episode"`
		Progress    float64       `json:"progress"`
		IsFinished  bool          `json:"is_finished"`
		UpdatedAt   string        `json:"updated_at"`
	}

	var result []HistoryItem
	for _, h := range histories {
		d := dramaMap[h.DramaID]
		finished := d != nil && d.TotalEpisodes > 0 && h.Progress >= d.TotalEpisodes
		result = append(result, HistoryItem{
			Drama:       d,
			LastEpisode: h.Progress,
			Progress:    0,
			IsFinished:  finished,
			UpdatedAt:   h.UpdatedAt.Format("2006-01-02 15:04:05"),
		})
	}
	utils.Success(c, result)
}

func GetCollections(c *gin.Context) {
	userID := c.GetUint("user_id")
	var collects []models.UserCollect
	database.DB.Where("user_id = ? AND active = ?", userID, true).Order("created_at DESC").Find(&collects)

	var dramaIDs []uint
	for _, col := range collects {
		dramaIDs = append(dramaIDs, col.DramaID)
	}

	var dramas []models.Drama
	if len(dramaIDs) > 0 {
		database.DB.Where("id IN ?", dramaIDs).Find(&dramas)
	}
	utils.Success(c, dramas)
}

func GetLikedEpisodes(c *gin.Context) {
	userID := c.GetUint("user_id")
	var likes []models.UserLike
	database.DB.Where("user_id = ? AND active = ?", userID, true).Order("created_at DESC").Limit(50).Find(&likes)

	var episodeIDs []uint
	for _, l := range likes {
		episodeIDs = append(episodeIDs, l.EpisodeID)
	}

	type EpisodeWithDrama struct {
		models.Episode
		Drama *models.Drama `json:"drama" gorm:"-"`
	}

	var result []EpisodeWithDrama
	if len(episodeIDs) > 0 {
		var episodes []models.Episode
		database.DB.Where("id IN ?", episodeIDs).Find(&episodes)

		dramaIDs := map[uint]bool{}
		for _, ep := range episodes {
			dramaIDs[ep.DramaID] = true
		}
		var ids []uint
		for id := range dramaIDs {
			ids = append(ids, id)
		}
		var dramas []models.Drama
		database.DB.Where("id IN ?", ids).Find(&dramas)
		dramaMap := map[uint]*models.Drama{}
		for i := range dramas {
			dramaMap[dramas[i].ID] = &dramas[i]
		}

		for _, ep := range episodes {
			item := EpisodeWithDrama{Episode: ep, Drama: dramaMap[ep.DramaID]}
			result = append(result, item)
		}
	}
	utils.Success(c, result)
}
