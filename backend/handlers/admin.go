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

	var user models.User
	if err := database.DB.Where("username = ? AND role = ?", req.Username, "admin").First(&user).Error; err != nil {
		utils.BadRequest(c, "管理员账号不存在")
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)); err != nil {
		utils.BadRequest(c, "密码错误")
		return
	}

	token, err := middleware.GenerateAdminToken(user.ID)
	if err != nil {
		utils.ServerError(c, "生成Token失败")
		return
	}

	utils.Success(c, gin.H{"token": token, "user": user})
}

func AdminDashboard(c *gin.Context) {
	var userCount, dramaCount, episodeCount, commentCount int64
	database.DB.Model(&models.User{}).Count(&userCount)
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

	go syncDramaToRankingCache(drama)

	utils.Success(c, drama)
}

func syncDramaToRankingCache(drama models.Drama) {
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

	var users []models.User
	var total int64
	query := database.DB.Model(&models.User{})
	if keyword != "" {
		query = query.Where("username LIKE ? OR nickname LIKE ?", "%"+keyword+"%", "%"+keyword+"%")
	}
	query.Count(&total)
	query.Order("id DESC").Offset((page - 1) * pageSize).Limit(pageSize).Find(&users)

	utils.Success(c, gin.H{"list": users, "total": total, "page": page, "page_size": pageSize})
}

func AdminUpdateUser(c *gin.Context) {
	id := c.Param("id")
	var user models.User
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
	database.DB.Delete(&models.Comment{}, id)
	utils.Success(c, nil)
}

// --- Category Admin ---

func AdminGetCategories(c *gin.Context) {
	var categories []models.Category
	database.DB.Order("sort ASC").Find(&categories)
	utils.Success(c, categories)
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
