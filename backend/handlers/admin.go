package handlers

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"
	"short-drama-backend/database"
	"short-drama-backend/middleware"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func AdminLogin(c *gin.Context) {
	var req struct {
		Username string `json:"username" binding:"required"`
		Password string `json:"password" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "请输入用户名和密码")
		return
	}

	var admin models.Admin
	if err := database.DB.Where("username = ?", req.Username).First(&admin).Error; err != nil {
		utils.BadRequest(c, "管理员账号不存在")
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(admin.PasswordHash), []byte(req.Password)); err != nil {
		utils.BadRequest(c, "密码错误")
		return
	}

	var permissions string
	if admin.RoleID > 0 {
		var role models.AdminRole
		if database.DB.First(&role, admin.RoleID).Error == nil {
			permissions = role.Permissions
		}
	}
	if permissions == "" {
		permissions = models.AllPermissions
	}

	token, err := middleware.GenerateAdminToken(admin.ID, admin.RoleID)
	if err != nil {
		utils.ServerError(c, "生成Token失败")
		return
	}

	utils.Success(c, gin.H{
		"token":       token,
		"user":        admin,
		"permissions": permissions,
	})
}

func AdminDashboard(c *gin.Context) {
	var userCount, dramaCount, episodeCount, commentCount int64
	database.DB.Model(&models.AppUser{}).Count(&userCount)
	database.DB.Model(&models.Drama{}).Count(&dramaCount)
	database.DB.Model(&models.Episode{}).Count(&episodeCount)
	database.DB.Model(&models.Comment{}).Count(&commentCount)

	utils.Success(c, gin.H{
		"user_count":    userCount,
		"drama_count":   dramaCount,
		"episode_count": episodeCount,
		"comment_count": commentCount,
	})
}

// --- Drama Admin CRUD ---

func AdminGetDramas(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	keyword := c.Query("keyword")

	var dramas []models.Drama
	var total int64
	query := database.DB.Model(&models.Drama{})
	if keyword != "" {
		query = query.Where("title LIKE ?", "%"+keyword+"%")
	}
	query.Count(&total)
	query.Order("id DESC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&dramas)

	utils.Success(c, gin.H{"list": dramas, "total": total, "page": page, "page_size": pageSize})
}

func AdminCreateDrama(c *gin.Context) {
	var drama models.Drama
	if err := c.ShouldBindJSON(&drama); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	database.DB.Create(&drama)
	utils.Success(c, drama)
}

func AdminUpdateDrama(c *gin.Context) {
	id := c.Param("id")
	var drama models.Drama
	if err := database.DB.First(&drama, id).Error; err != nil {
		utils.BadRequest(c, "剧集不存在")
		return
	}
	if err := c.ShouldBindJSON(&drama); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	database.DB.Save(&drama)

	go syncDramaCache(drama)

	utils.Success(c, drama)
}

func syncDramaCache(drama models.Drama) {
	utils.CacheDelete("drama:" + fmt.Sprintf("%d", drama.ID))
	utils.CacheDelete("home:page")
	utils.CacheDelete("hot_dramas")

	if !drama.Enabled {
		removeDramaFromRankingCache(drama.ID)
	} else {
		UpdateDramaInRankingCache(drama.ID, map[string]interface{}{
			"id":             drama.ID,
			"title":          drama.Title,
			"cover_url":      drama.CoverURL,
			"description":    drama.Description,
			"category":       drama.Category,
			"tags":           drama.Tags,
			"total_episodes": drama.TotalEpisodes,
			"rating":         drama.Rating,
			"heat":           drama.Heat,
			"status":         drama.Status,
		})
	}
}

func AdminDeleteDrama(c *gin.Context) {
	id := c.Param("id")
	dramaID := parseAdminUint(id)
	database.DB.Delete(&models.Drama{}, id)
	database.DB.Where("drama_id = ?", id).Delete(&models.Episode{})

	go removeDramaFromRankingCache(dramaID)

	utils.Success(c, nil)
}

func parseAdminUint(s string) uint {
	v, _ := strconv.ParseUint(s, 10, 64)
	return uint(v)
}

func removeDramaFromRankingCache(dramaID uint) {
	keys := []string{"ranking:hot:full", "ranking:rising:full", "ranking:rating:full"}
	for _, key := range keys {
		var cached []map[string]interface{}
		if err := utils.CacheGet(key, &cached); err != nil {
			continue
		}
		filtered := make([]map[string]interface{}, 0, len(cached))
		for _, item := range cached {
			drama, ok := item["drama"].(map[string]interface{})
			if !ok {
				filtered = append(filtered, item)
				continue
			}
			var itemID uint
			if v, ok := drama["id"].(float64); ok {
				itemID = uint(v)
			}
			if itemID != dramaID {
				filtered = append(filtered, item)
			}
		}
		// Re-rank
		for i := range filtered {
			filtered[i]["rank"] = i + 1
		}
		utils.CacheSet(key, filtered, 25*time.Hour)
	}
	utils.CacheDelete("home:page")
}

// --- Episode Admin CRUD ---

func AdminGetEpisodes(c *gin.Context) {
	dramaID := c.Query("drama_id")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "50"))

	var episodes []models.Episode
	var total int64
	query := database.DB.Model(&models.Episode{})
	if dramaID != "" {
		query = query.Where("drama_id = ?", dramaID)
	}
	query.Count(&total)
	query.Order("episode_number ASC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&episodes)

	utils.Success(c, gin.H{"list": episodes, "total": total, "page": page, "page_size": pageSize})
}

func AdminCreateEpisode(c *gin.Context) {
	var episode models.Episode
	if err := c.ShouldBindJSON(&episode); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}

	var existing models.Episode
	if database.DB.Where("drama_id = ? AND episode_number = ?", episode.DramaID, episode.EpisodeNumber).First(&existing).RowsAffected > 0 {
		utils.BadRequest(c, fmt.Sprintf("第 %d 集已存在", episode.EpisodeNumber))
		return
	}

	database.DB.Create(&episode)
	utils.Success(c, episode)
}

func AdminUpdateEpisode(c *gin.Context) {
	id := c.Param("id")
	var episode models.Episode
	if err := database.DB.First(&episode, id).Error; err != nil {
		utils.BadRequest(c, "分集不存在")
		return
	}
	if err := c.ShouldBindJSON(&episode); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}

	var conflict models.Episode
	if database.DB.Where("drama_id = ? AND episode_number = ? AND id != ?", episode.DramaID, episode.EpisodeNumber, id).First(&conflict).RowsAffected > 0 {
		utils.BadRequest(c, fmt.Sprintf("第 %d 集已存在", episode.EpisodeNumber))
		return
	}

	database.DB.Save(&episode)
	utils.Success(c, episode)
}

func AdminDeleteEpisode(c *gin.Context) {
	id := c.Param("id")
	database.DB.Delete(&models.Episode{}, id)
	utils.Success(c, nil)
}

// --- User Admin ---

func AdminGetUsers(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	keyword := c.Query("keyword")

	var users []models.AppUser
	var total int64
	query := database.DB.Model(&models.AppUser{})
	if keyword != "" {
		query = query.Where("username LIKE ? OR nickname LIKE ?", "%"+keyword+"%", "%"+keyword+"%")
	}
	query.Count(&total)
	query.Order("id DESC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&users)

	utils.Success(c, gin.H{"list": users, "total": total, "page": page, "page_size": pageSize})
}

func AdminUpdateUser(c *gin.Context) {
	id := c.Param("id")
	var user models.AppUser
	if err := database.DB.First(&user, id).Error; err != nil {
		utils.BadRequest(c, "用户不存在")
		return
	}
	var req struct {
		Status int `json:"status"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	database.DB.Model(&user).Update("status", req.Status)
	utils.Success(c, user)
}

// --- Admin Management ---

func AdminGetAdmins(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	keyword := c.Query("keyword")

	var admins []models.Admin
	var total int64
	query := database.DB.Model(&models.Admin{})
	if keyword != "" {
		query = query.Where("username LIKE ? OR nickname LIKE ?", "%"+keyword+"%", "%"+keyword+"%")
	}
	query.Count(&total)
	query.Order("id DESC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&admins)

	var roles []models.AdminRole
	database.DB.Find(&roles)
	roleMap := map[uint]string{}
	for _, r := range roles {
		roleMap[r.ID] = r.Name
	}

	type AdminItem struct {
		models.Admin
		RoleName string `json:"role_name"`
	}
	items := make([]AdminItem, len(admins))
	for i, a := range admins {
		items[i] = AdminItem{Admin: a, RoleName: roleMap[a.RoleID]}
	}

	utils.Success(c, gin.H{"list": items, "total": total, "page": page, "page_size": pageSize})
}

func AdminCreateAdmin(c *gin.Context) {
	var req struct {
		Username string `json:"username" binding:"required"`
		Password string `json:"password" binding:"required,min=6"`
		Nickname string `json:"nickname"`
		RoleID   uint   `json:"role_id"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "请填写用户名和密码（密码至少6位）")
		return
	}

	var existing models.Admin
	if database.DB.Where("username = ?", req.Username).First(&existing).RowsAffected > 0 {
		utils.BadRequest(c, "用户名已存在")
		return
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		utils.ServerError(c, "服务器错误")
		return
	}

	nickname := req.Nickname
	if nickname == "" {
		nickname = req.Username
	}

	admin := models.Admin{
		Username:     req.Username,
		Nickname:     nickname,
		PasswordHash: string(hash),
		RoleID:       req.RoleID,
		Status:       1,
	}
	database.DB.Create(&admin)
	utils.Success(c, admin)
}

func AdminUpdateAdmin(c *gin.Context) {
	id := c.Param("id")
	var admin models.Admin
	if err := database.DB.First(&admin, id).Error; err != nil {
		utils.BadRequest(c, "管理员不存在")
		return
	}

	var req struct {
		Nickname string `json:"nickname"`
		Password string `json:"password"`
		Status   *int   `json:"status"`
		RoleID   *uint  `json:"role_id"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}

	updates := map[string]interface{}{}
	if req.Nickname != "" {
		updates["nickname"] = req.Nickname
	}
	if req.Password != "" {
		hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
		if err != nil {
			utils.ServerError(c, "服务器错误")
			return
		}
		updates["password_hash"] = string(hash)
	}
	if req.RoleID != nil {
		updates["role_id"] = *req.RoleID
	}
	if req.Status != nil {
		updates["status"] = *req.Status
	}

	if len(updates) > 0 {
		database.DB.Model(&admin).Updates(updates)
	}

	database.DB.First(&admin, id)
	utils.Success(c, admin)
}

func AdminDeleteAdmin(c *gin.Context) {
	id := c.Param("id")
	currentUserID := c.GetUint("user_id")

	if fmt.Sprintf("%d", currentUserID) == id {
		utils.BadRequest(c, "不能删除自己")
		return
	}

	var admin models.Admin
	if err := database.DB.First(&admin, id).Error; err != nil {
		utils.BadRequest(c, "管理员不存在")
		return
	}

	database.DB.Delete(&admin)
	utils.Success(c, nil)
}

// --- Comment Admin ---

func AdminGetComments(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))

	var comments []models.Comment
	var total int64
	database.DB.Model(&models.Comment{}).Count(&total)
	database.DB.Order("id DESC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&comments)

	utils.Success(c, gin.H{"list": comments, "total": total, "page": page, "page_size": pageSize})
}

func AdminDeleteComment(c *gin.Context) {
	id := c.Param("id")
	var comment models.Comment
	if database.DB.First(&comment, id).Error == nil {
		database.DB.Delete(&comment)
		go func() {
			database.DB.Model(&models.Episode{}).Where("id = ?", comment.EpisodeID).
				UpdateColumn("comment_count", gorm.Expr("GREATEST(comment_count - 1, 0)"))
		}()
	}
	utils.Success(c, nil)
}

// --- Category Admin ---

func AdminGetCategories(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "10"))

	var categories []models.Category
	var total int64
	database.DB.Model(&models.Category{}).Count(&total)
	database.DB.Order("sort ASC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&categories)

	utils.Success(c, gin.H{"list": categories, "total": total, "page": page, "page_size": pageSize})
}

func AdminCreateCategory(c *gin.Context) {
	var cat models.Category
	if err := c.ShouldBindJSON(&cat); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	database.DB.Create(&cat)
	utils.Success(c, cat)
}

func AdminUpdateCategory(c *gin.Context) {
	id := c.Param("id")
	var cat models.Category
	if err := database.DB.First(&cat, id).Error; err != nil {
		utils.BadRequest(c, "分类不存在")
		return
	}
	if err := c.ShouldBindJSON(&cat); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	database.DB.Save(&cat)
	utils.Success(c, cat)
}

func AdminDeleteCategory(c *gin.Context) {
	id := c.Param("id")
	database.DB.Delete(&models.Category{}, id)
	utils.Success(c, nil)
}

// --- File Upload ---

func UploadVideo(c *gin.Context) {
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		utils.BadRequest(c, "请选择文件")
		return
	}
	defer file.Close()

	uploadDir := "./uploads/videos"
	os.MkdirAll(uploadDir, os.ModePerm)

	filename := fmt.Sprintf("%d_%s", time.Now().UnixMilli(), header.Filename)
	dst := filepath.Join(uploadDir, filename)

	out, err := os.Create(dst)
	if err != nil {
		utils.ServerError(c, "保存文件失败")
		return
	}
	defer out.Close()
	io.Copy(out, file)

	utils.Success(c, gin.H{
		"path":     dst,
		"filename": filename,
		"size":     header.Size,
	})
}

func UploadImage(c *gin.Context) {
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		utils.BadRequest(c, "请选择文件")
		return
	}
	defer file.Close()

	uploadDir := "./uploads/images"
	os.MkdirAll(uploadDir, os.ModePerm)

	filename := fmt.Sprintf("%d_%s", time.Now().UnixMilli(), header.Filename)
	dst := filepath.Join(uploadDir, filename)

	out, err := os.Create(dst)
	if err != nil {
		utils.ServerError(c, "保存文件失败")
		return
	}
	defer out.Close()
	io.Copy(out, file)

	utils.Success(c, gin.H{
		"url":      "/uploads/images/" + filename,
		"filename": filename,
		"size":     header.Size,
	})
}

// --- Role CRUD ---

func AdminGetRoles(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "10"))

	var roles []models.AdminRole
	var total int64
	database.DB.Model(&models.AdminRole{}).Count(&total)
	database.DB.Order("id ASC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&roles)

	utils.Success(c, gin.H{"list": roles, "total": total, "page": page, "page_size": pageSize})
}

func AdminCreateRole(c *gin.Context) {
	var req struct {
		Name        string `json:"name" binding:"required"`
		Description string `json:"description"`
		Permissions string `json:"permissions"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "请填写角色名称")
		return
	}
	var existing models.AdminRole
	if database.DB.Where("name = ?", req.Name).First(&existing).RowsAffected > 0 {
		utils.BadRequest(c, "角色名称已存在")
		return
	}
	role := models.AdminRole{
		Name:        req.Name,
		Description: req.Description,
		Permissions: req.Permissions,
	}
	database.DB.Create(&role)
	utils.Success(c, role)
}

func AdminUpdateRole(c *gin.Context) {
	id := c.Param("id")
	var role models.AdminRole
	if err := database.DB.First(&role, id).Error; err != nil {
		utils.BadRequest(c, "角色不存在")
		return
	}
	var req struct {
		Name        string `json:"name"`
		Description string `json:"description"`
		Permissions string `json:"permissions"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	updates := map[string]interface{}{}
	if req.Name != "" {
		updates["name"] = req.Name
	}
	if req.Description != "" {
		updates["description"] = req.Description
	}
	updates["permissions"] = req.Permissions
	database.DB.Model(&role).Updates(updates)
	database.DB.First(&role, id)
	utils.Success(c, role)
}

func AdminDeleteRole(c *gin.Context) {
	id := c.Param("id")
	var role models.AdminRole
	if err := database.DB.First(&role, id).Error; err != nil {
		utils.BadRequest(c, "角色不存在")
		return
	}
	var count int64
	database.DB.Model(&models.Admin{}).Where("role_id = ?", id).Count(&count)
	if count > 0 {
		utils.BadRequest(c, fmt.Sprintf("该角色下有 %d 个管理员，请先移除", count))
		return
	}
	database.DB.Delete(&role)
	utils.Success(c, nil)
}
