package handlers

import (
	"errors"
	"fmt"
	"math"
	"sort"
	"strconv"
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

// 列表接口中每条主评下嵌入的回复预览条数；须与 App 端分页 page_size 约定一致
const replyPreviewPerRoot = 5

// 与 MySQL TIMESTAMPDIFF(HOUR, t, NOW()) 在正的时间差下一致（整点小时向零截断）。
func mysqlHourDiffFromNow(t time.Time) int {
	d := time.Since(t)
	if d < 0 {
		return 0
	}
	return int(d.Hours())
}

type replyAggRow struct {
	ID            uint
	EpisodeID     uint
	UserID        uint
	ParentID      uint
	ReplyToUserID uint
	Content       string
	LikesCount    int
	CreatedAt     time.Time
	ChildCnt      int64 `gorm:"column:child_cnt"`
}

func (r replyAggRow) toComment() models.Comment {
	return models.Comment{
		ID: r.ID, EpisodeID: r.EpisodeID, UserID: r.UserID,
		ParentID: r.ParentID, ReplyToUserID: r.ReplyToUserID,
		Content: r.Content, LikesCount: r.LikesCount, CreatedAt: r.CreatedAt,
	}
}

// 批量补全用户昵称/头像，避免每条评论单独查 app_users。
func batchEnrichCommentMeta(comments []*models.Comment) {
	if len(comments) == 0 {
		return
	}
	uidSet := make(map[uint]struct{})
	for _, cm := range comments {
		if cm == nil {
			continue
		}
		if cm.UserID > 0 {
			uidSet[cm.UserID] = struct{}{}
		}
		if cm.ReplyToUserID > 0 {
			uidSet[cm.ReplyToUserID] = struct{}{}
		}
	}
	if len(uidSet) == 0 {
		return
	}
	uids := make([]uint, 0, len(uidSet))
	for id := range uidSet {
		uids = append(uids, id)
	}
	var users []models.AppUser
	database.DB.Where("id IN ?", uids).Find(&users)
	byID := make(map[uint]models.AppUser, len(users))
	for i := range users {
		byID[users[i].ID] = users[i]
	}
	for _, cm := range comments {
		if cm == nil {
			continue
		}
		if u, ok := byID[cm.UserID]; ok {
			cm.Nickname = u.Nickname
			cm.Avatar = u.Avatar
		}
		if cm.ReplyToUserID > 0 {
			if u, ok := byID[cm.ReplyToUserID]; ok {
				cm.ReplyToNickname = u.Nickname
			}
		}
	}
}

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

func commentLikedSet(userID uint, ids []uint) map[uint]bool {
	if userID == 0 || len(ids) == 0 {
		return nil
	}
	var likedRecords []models.CommentLike
	database.DB.Where("user_id = ? AND comment_id IN ?", userID, ids).Find(&likedRecords)
	m := make(map[uint]bool)
	for _, lr := range likedRecords {
		m[lr.CommentID] = true
	}
	return m
}

func applyCommentLikedAndTimeAgo(cm *models.Comment, likedSet map[uint]bool) {
	cm.TimeAgo = timeAgo(cm.CreatedAt)
	if likedSet != nil {
		cm.Liked = likedSet[cm.ID]
	}
}

func GetComments(c *gin.Context) {
	episodeID := c.Param("id")
	userID := optionalUserID(c)

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "15"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 {
		pageSize = 15
	}
	if pageSize > 30 {
		pageSize = 30
	}
	offset := (page - 1) * pageSize

	var roots []models.Comment
	errRoots := database.DB.Raw(`
SELECT c.id, c.episode_id, c.user_id, c.parent_id, c.reply_to_user_id, c.content, c.likes_count, c.created_at
FROM comments c
LEFT JOIN (
  SELECT parent_id, COUNT(*) AS cnt
  FROM comments
  WHERE episode_id = ?
  GROUP BY parent_id
) ch ON ch.parent_id = c.id
WHERE c.episode_id = ? AND c.parent_id = 0
ORDER BY (100 + c.likes_count + 2 * COALESCE(ch.cnt, 0) - TIMESTAMPDIFF(HOUR, c.created_at, NOW())) DESC,
         c.created_at DESC, c.id DESC
LIMIT ? OFFSET ?
`, episodeID, episodeID, pageSize+1, offset).Scan(&roots).Error
	if errRoots != nil {
		utils.ServerError(c, "加载评论失败")
		return
	}

	hasMore := len(roots) > pageSize
	if hasMore {
		roots = roots[:pageSize]
	}

	replyMap := make(map[uint][]models.Comment)
	replyTotal := make(map[uint]int64)
	if len(roots) > 0 {
		rootIDs := make([]uint, len(roots))
		for i := range roots {
			rootIDs[i] = roots[i].ID
		}

		var cntRows []struct {
			ParentID uint  `gorm:"column:parent_id"`
			Cnt      int64 `gorm:"column:cnt"`
		}
		database.DB.Model(&models.Comment{}).
			Select("parent_id, COUNT(*) as cnt").
			Where("episode_id = ? AND parent_id IN ?", episodeID, rootIDs).
			Group("parent_id").Scan(&cntRows)
		for _, cr := range cntRows {
			replyTotal[cr.ParentID] = cr.Cnt
		}

		type previewRow struct {
			ID            uint      `gorm:"column:id"`
			EpisodeID     uint      `gorm:"column:episode_id"`
			UserID        uint      `gorm:"column:user_id"`
			ParentID      uint      `gorm:"column:parent_id"`
			ReplyToUserID uint      `gorm:"column:reply_to_user_id"`
			Content       string    `gorm:"column:content"`
			LikesCount    int       `gorm:"column:likes_count"`
			CreatedAt     time.Time `gorm:"column:created_at"`
			RN            int       `gorm:"column:rn"`
		}
		var previewRows []previewRow
		err := database.DB.Raw(`
SELECT c.id, c.episode_id, c.user_id, c.parent_id, c.reply_to_user_id, c.content, c.likes_count, c.created_at, x.rn
FROM comments c
INNER JOIN (
  SELECT sub.id, ROW_NUMBER() OVER (PARTITION BY sub.parent_id ORDER BY (
    TIMESTAMPDIFF(HOUR, sub.created_at, NOW())
    + sub.likes_count
    + 2 * COALESCE(ch2.cnt, 0)
  ) DESC, sub.created_at ASC, sub.id ASC) AS rn
  FROM comments sub
  LEFT JOIN (
    SELECT parent_id, COUNT(*) AS cnt
    FROM comments
    WHERE episode_id = ?
    GROUP BY parent_id
  ) ch2 ON ch2.parent_id = sub.id
  WHERE sub.episode_id = ? AND sub.parent_id IN ?
) x ON c.id = x.id
WHERE x.rn <= ?
`, episodeID, episodeID, rootIDs, replyPreviewPerRoot+1).Scan(&previewRows).Error

		if err != nil {
			replyMap = fallbackReplyPreviewPerRoot(episodeID, rootIDs, replyPreviewPerRoot)
		} else {
			byParent := make(map[uint][]previewRow)
			for _, pr := range previewRows {
				pid := pr.ParentID
				byParent[pid] = append(byParent[pid], pr)
			}
			for pid, list := range byParent {
				if len(list) > replyPreviewPerRoot {
					list = list[:replyPreviewPerRoot]
				}
				reps := make([]models.Comment, len(list))
				for j, pr := range list {
					reps[j] = models.Comment{
						ID: pr.ID, EpisodeID: pr.EpisodeID, UserID: pr.UserID,
						ParentID: pr.ParentID, ReplyToUserID: pr.ReplyToUserID,
						Content: pr.Content, LikesCount: pr.LikesCount, CreatedAt: pr.CreatedAt,
					}
				}
				replyMap[pid] = reps
			}
		}
	}

	var flatIDs []uint
	for _, r := range roots {
		flatIDs = append(flatIDs, r.ID)
		for _, rep := range replyMap[r.ID] {
			flatIDs = append(flatIDs, rep.ID)
		}
	}
	likedSet := commentLikedSet(userID, flatIDs)

	ptrs := make([]*models.Comment, 0, len(flatIDs))
	for i := range roots {
		ptrs = append(ptrs, &roots[i])
	}
	for i := range roots {
		pid := roots[i].ID
		reps := replyMap[pid]
		for j := range reps {
			ptrs = append(ptrs, &reps[j])
		}
	}
	batchEnrichCommentMeta(ptrs)
	for _, cm := range ptrs {
		applyCommentLikedAndTimeAgo(cm, likedSet)
	}
	for i := range roots {
		pid := roots[i].ID
		roots[i].ReplyCount = int(replyTotal[pid])
		reps := replyMap[pid]
		roots[i].Replies = reps
		roots[i].HasMoreReplies = replyTotal[pid] > int64(len(reps))
	}

	utils.Success(c, gin.H{
		"list":       roots,
		"has_more":   hasMore,
		"page":       page,
		"page_size":  pageSize,
	})
}

// 不支持窗口函数时：按剧集批量拉取各主评下回复，内存排序后截断，避免 N+1 查询。
func fallbackReplyPreviewPerRoot(episodeID string, rootIDs []uint, preview int) map[uint][]models.Comment {
	out := make(map[uint][]models.Comment, len(rootIDs))
	for _, rid := range rootIDs {
		out[rid] = nil
	}
	if len(rootIDs) == 0 {
		return out
	}
	var rows []replyAggRow
	err := database.DB.Raw(`
SELECT c.id, c.episode_id, c.user_id, c.parent_id, c.reply_to_user_id, c.content, c.likes_count, c.created_at,
       COALESCE(ch.cnt, 0) AS child_cnt
FROM comments c
LEFT JOIN (
  SELECT parent_id, COUNT(*) AS cnt
  FROM comments
  WHERE episode_id = ?
  GROUP BY parent_id
) ch ON ch.parent_id = c.id
WHERE c.episode_id = ? AND c.parent_id IN ?
`, episodeID, episodeID, rootIDs).Scan(&rows).Error
	if err != nil {
		return out
	}
	byParent := make(map[uint][]replyAggRow)
	for _, r := range rows {
		byParent[r.ParentID] = append(byParent[r.ParentID], r)
	}
	for _, rid := range rootIDs {
		list := byParent[rid]
		sort.Slice(list, func(i, j int) bool {
			si := mysqlHourDiffFromNow(list[i].CreatedAt) + list[i].LikesCount + int(2*list[i].ChildCnt)
			sj := mysqlHourDiffFromNow(list[j].CreatedAt) + list[j].LikesCount + int(2*list[j].ChildCnt)
			if si != sj {
				return si > sj
			}
			if !list[i].CreatedAt.Equal(list[j].CreatedAt) {
				return list[i].CreatedAt.Before(list[j].CreatedAt)
			}
			return list[i].ID < list[j].ID
		})
		if len(list) > preview {
			list = list[:preview]
		}
		reps := make([]models.Comment, len(list))
		for j := range list {
			reps[j] = list[j].toComment()
		}
		out[rid] = reps
	}
	return out
}

func GetCommentReplies(c *gin.Context) {
	episodeIDStr := c.Param("id")
	rootIDStr := c.Param("root_id")
	userID := optionalUserID(c)

	var root models.Comment
	if database.DB.First(&root, rootIDStr).Error != nil || root.ParentID != 0 {
		utils.BadRequest(c, "评论不存在")
		return
	}
	epID64, err := strconv.ParseUint(episodeIDStr, 10, 32)
	if err != nil || root.EpisodeID != uint(epID64) {
		utils.BadRequest(c, "评论不存在")
		return
	}

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "5"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 {
		pageSize = replyPreviewPerRoot
	}
	if pageSize > 30 {
		pageSize = 30
	}
	offset := (page - 1) * pageSize

	var list []models.Comment
	errList := database.DB.Raw(`
SELECT c.id, c.episode_id, c.user_id, c.parent_id, c.reply_to_user_id, c.content, c.likes_count, c.created_at
FROM comments c
LEFT JOIN (
  SELECT parent_id, COUNT(*) AS cnt
  FROM comments
  WHERE episode_id = ?
  GROUP BY parent_id
) ch ON ch.parent_id = c.id
WHERE c.episode_id = ? AND c.parent_id = ?
ORDER BY (TIMESTAMPDIFF(HOUR, c.created_at, NOW()) + c.likes_count + 2 * COALESCE(ch.cnt, 0)) DESC,
         c.created_at ASC, c.id ASC
LIMIT ? OFFSET ?
`, root.EpisodeID, root.EpisodeID, root.ID, pageSize+1, offset).Scan(&list).Error
	if errList != nil {
		utils.ServerError(c, "加载回复失败")
		return
	}
	hasMore := len(list) > pageSize
	if hasMore {
		list = list[:pageSize]
	}

	ids := make([]uint, len(list))
	for i := range list {
		ids[i] = list[i].ID
	}
	likedSet := commentLikedSet(userID, ids)
	ptrs := make([]*models.Comment, len(list))
	for i := range list {
		ptrs[i] = &list[i]
	}
	batchEnrichCommentMeta(ptrs)
	for _, cm := range ptrs {
		applyCommentLikedAndTimeAgo(cm, likedSet)
	}

	utils.Success(c, gin.H{
		"list":      list,
		"has_more":  hasMore,
		"page":      page,
		"page_size": pageSize,
	})
}

func PostComment(c *gin.Context) {
	userID := c.GetUint("user_id")
	episodeID := c.Param("id")

	var req struct {
		Content          string `json:"content" binding:"required"`
		ParentID         uint   `json:"parent_id"`
		ReplyToCommentID uint   `json:"reply_to_comment_id"`
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
		UserID:    userID,
		Content:   req.Content,
		EpisodeID: ep.ID,
	}

	// 优先用 reply_to_comment_id 解析主评 id，避免客户端未带上 parent_id 时回复被写成 parent_id=0 的主评
	switch {
	case req.ReplyToCommentID > 0:
		var tgt models.Comment
		if database.DB.Where("id = ? AND episode_id = ?", req.ReplyToCommentID, ep.ID).First(&tgt).Error != nil {
			utils.BadRequest(c, "回复对象不存在")
			return
		}
		var rootID uint
		if tgt.ParentID == 0 {
			rootID = tgt.ID
		} else {
			rootID = tgt.ParentID
		}
		var root models.Comment
		if database.DB.Where("id = ? AND episode_id = ? AND parent_id = 0", rootID, ep.ID).First(&root).Error != nil {
			utils.BadRequest(c, "回复的评论不存在")
			return
		}
		comment.ParentID = rootID
		comment.ReplyToUserID = tgt.UserID
	case req.ParentID > 0:
		var root models.Comment
		if database.DB.Where("id = ? AND episode_id = ?", req.ParentID, ep.ID).First(&root).Error != nil {
			utils.BadRequest(c, "回复的评论不存在")
			return
		}
		if root.ParentID != 0 {
			utils.BadRequest(c, "仅支持回复主评论")
			return
		}
		comment.ParentID = req.ParentID
	default:
		// 主评论
	}

	if err := database.DB.Create(&comment).Error; err != nil {
		utils.ServerError(c, "发布失败")
		return
	}

	go func() {
		database.DB.Model(&models.Episode{}).Where("id = ?", ep.ID).
			UpdateColumn("comment_count", gorm.Expr("comment_count + 1"))

		database.DB.Model(&models.Drama{}).Where("id = ?", ep.DramaID).
			UpdateColumn("heat", gorm.Expr("heat + 100"))

		database.DB.Exec(
			"INSERT INTO drama_stats (drama_id, total_comments, heat_from_comments, updated_at) VALUES (?, 1, 100, NOW()) "+
				"ON DUPLICATE KEY UPDATE total_comments = total_comments + 1, heat_from_comments = heat_from_comments + 100, updated_at = NOW()", ep.DramaID)
	}()

	var user models.AppUser
	database.DB.First(&user, userID)
	comment.Nickname = user.Nickname
	comment.Avatar = user.Avatar
	comment.TimeAgo = "刚刚"
	if comment.ReplyToUserID > 0 {
		var ru models.AppUser
		if database.DB.First(&ru, comment.ReplyToUserID).RowsAffected > 0 {
			comment.ReplyToNickname = ru.Nickname
		}
	}
	likedSet := commentLikedSet(userID, []uint{comment.ID})
	if likedSet != nil {
		comment.Liked = likedSet[comment.ID]
	}

	utils.Success(c, comment)
}

func LikeComment(c *gin.Context) {
	userID := c.GetUint("user_id")
	commentIDStr := c.Param("id")
	cid64, err := strconv.ParseUint(commentIDStr, 10, 32)
	if err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	cid := uint(cid64)

	var liked bool
	var likesCount int
	err = database.DB.Transaction(func(tx *gorm.DB) error {
		var existing models.CommentLike
		err := tx.Where("user_id = ? AND comment_id = ?", userID, cid).First(&existing).Error
		if err == nil {
			if err := tx.Delete(&existing).Error; err != nil {
				return err
			}
			if err := tx.Model(&models.Comment{}).Where("id = ?", cid).
				UpdateColumn("likes_count", gorm.Expr("GREATEST(likes_count - 1, 0)")).Error; err != nil {
				return err
			}
			liked = false
		} else if !errors.Is(err, gorm.ErrRecordNotFound) {
			return err
		} else {
			like := models.CommentLike{UserID: userID, CommentID: cid}
			if err := tx.Create(&like).Error; err != nil {
				return err
			}
			if err := tx.Model(&models.Comment{}).Where("id = ?", cid).
				UpdateColumn("likes_count", gorm.Expr("likes_count + 1")).Error; err != nil {
				return err
			}
			liked = true
		}
		var cm models.Comment
		if err := tx.Select("likes_count").First(&cm, cid).Error; err != nil {
			return err
		}
		likesCount = cm.LikesCount
		return nil
	})
	if err != nil {
		utils.ServerError(c, "操作失败")
		return
	}
	utils.Success(c, gin.H{"liked": liked, "likes_count": likesCount})
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
