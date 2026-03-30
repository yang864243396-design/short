package handlers

import (
	"fmt"
	"math"
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func GetComments(c *gin.Context) {
	episodeID := c.Param("id")
	var comments []models.Comment
	database.DB.Where("episode_id = ?", episodeID).Order("likes_count DESC, created_at DESC").Limit(50).Find(&comments)

	for i := range comments {
		var user models.User
		if database.DB.First(&user, comments[i].UserID).RowsAffected > 0 {
			comments[i].Nickname = user.Nickname
			comments[i].Avatar = user.Avatar
		}
		comments[i].TimeAgo = timeAgo(comments[i].CreatedAt)
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

	comment := models.Comment{
		UserID:  userID,
		Content: req.Content,
	}
	fmt.Sscanf(episodeID, "%d", &comment.EpisodeID)
	database.DB.Create(&comment)

	var user models.User
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
		database.DB.Model(&models.Comment{}).Where("id = ?", commentID).UpdateColumn("likes_count", gormExpr("likes_count - 1"))
		utils.Success(c, gin.H{"liked": false})
		return
	}

	like := models.CommentLike{UserID: userID}
	fmt.Sscanf(commentID, "%d", &like.CommentID)
	database.DB.Create(&like)
	database.DB.Model(&models.Comment{}).Where("id = ?", commentID).UpdateColumn("likes_count", gormExpr("likes_count + 1"))
	utils.Success(c, gin.H{"liked": true})
}

func gormExpr(expr string) interface{} {
	return database.DB.Raw(expr)
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
