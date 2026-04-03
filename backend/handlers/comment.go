package handlers

import (
	"fmt"
	"math"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"gorm.io/gorm"
	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func optionalUserID(c *gin.Context) uint {
	header := c.GetHeader("Authorization")
	if header == "" || !strings.HasPrefix(header, "Bearer ") {
		return 0
	}
	tokenStr := strings.TrimPrefix(header, "Bearer ")
	cfg := config.Load()
	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
		return []byte(cfg.JWT.Secret), nil
	})
	if err != nil || !token.Valid {
		return 0
	}
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return 0
	}
	if uid, ok := claims["user_id"].(float64); ok {
		return uint(uid)
	}
	return 0
}

func GetComments(c *gin.Context) {
	episodeID := c.Param("id")
	var comments []models.Comment
	database.DB.Where("episode_id = ?", episodeID).Order("likes_count DESC, created_at DESC").Limit(50).Find(&comments)

	userID := optionalUserID(c)

	var likedSet map[uint]bool
	if userID > 0 && len(comments) > 0 {
		likedSet = make(map[uint]bool)
		var commentIDs []uint
		for _, cm := range comments {
			commentIDs = append(commentIDs, cm.ID)
		}
		var likedRecords []models.CommentLike
		database.DB.Where("user_id = ? AND comment_id IN ?", userID, commentIDs).Find(&likedRecords)
		for _, lr := range likedRecords {
			likedSet[lr.CommentID] = true
		}
	}

	for i := range comments {
		var user models.AppUser
		if database.DB.First(&user, comments[i].UserID).RowsAffected > 0 {
			comments[i].Nickname = user.Nickname
			comments[i].Avatar = user.Avatar
		}
		comments[i].TimeAgo = timeAgo(comments[i].CreatedAt)
		if likedSet != nil {
			comments[i].Liked = likedSet[comments[i].ID]
		}
	}

	utils.Success(c, comments)
}

func PostComment(c *gin.Context) {
	userID := c.GetUint("user_id")
	episodeID := c.Param("id")

	var req struct {
		Content string `json:"content" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "评论内容不能为空")
		return
	}

	var ep models.Episode
	if database.DB.First(&ep, episodeID).Error != nil {
		utils.BadRequest(c, "剧集不存在")
		return
	}

	comment := models.Comment{
		UserID:  userID,
		Content: req.Content,
	}
	fmt.Sscanf(episodeID, "%d", &comment.EpisodeID)
	database.DB.Create(&comment)

	go func() {
		database.DB.Model(&models.Episode{}).Where("id = ?", ep.ID).
			UpdateColumn("comment_count", gorm.Expr("comment_count + 1"))

		database.DB.Model(&models.Drama{}).Where("id = ?", ep.DramaID).
			UpdateColumn("heat", gorm.Expr("heat + 100"))

		database.DB.Exec(
			"INSERT INTO drama_stats (drama_id, total_comments, updated_at) VALUES (?, 1, NOW()) "+
				"ON DUPLICATE KEY UPDATE total_comments = total_comments + 1, updated_at = NOW()", ep.DramaID)
	}()

	var user models.AppUser
	database.DB.First(&user, userID)
	comment.Nickname = user.Nickname
	comment.Avatar = user.Avatar
	comment.TimeAgo = "刚刚"

	utils.Success(c, comment)
}

func LikeComment(c *gin.Context) {
	userID := c.GetUint("user_id")
	commentID := c.Param("id")

	var existing models.CommentLike
	result := database.DB.Where("user_id = ? AND comment_id = ?", userID, commentID).First(&existing)

	if result.RowsAffected > 0 {
		database.DB.Delete(&existing)
		database.DB.Model(&models.Comment{}).Where("id = ?", commentID).
			UpdateColumn("likes_count", gorm.Expr("GREATEST(likes_count - 1, 0)"))
		utils.Success(c, gin.H{"liked": false})
		return
	}

	like := models.CommentLike{UserID: userID}
	fmt.Sscanf(commentID, "%d", &like.CommentID)
	database.DB.Create(&like)
	database.DB.Model(&models.Comment{}).Where("id = ?", commentID).
		UpdateColumn("likes_count", gorm.Expr("likes_count + 1"))
	utils.Success(c, gin.H{"liked": true})
}

func timeAgo(t time.Time) string {
	diff := time.Since(t)
	minutes := int(math.Floor(diff.Minutes()))
	hours := int(math.Floor(diff.Hours()))
	days := int(math.Floor(diff.Hours() / 24))

	if minutes < 1 {
		return "刚刚"
	} else if minutes < 60 {
		return fmt.Sprintf("%d分钟前", minutes)
	} else if hours < 24 {
		return fmt.Sprintf("%d小时前", hours)
	} else if days < 30 {
		return fmt.Sprintf("%d天前", days)
	}
	return t.Format("01-02")
}
