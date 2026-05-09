package handlers

import (
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

const (
	appReleaseMaxUploadBytes = 50 * 1024 * 1024
	appReleaseVersionPattern = `^\d+\.\d+\.\d+$`
)

var appReleaseVersionRe = regexp.MustCompile(appReleaseVersionPattern)

func validAppReleaseVersion(s string) bool {
	return appReleaseVersionRe.MatchString(strings.TrimSpace(s))
}

func parseSemverThree(s string) (a, b, c int, ok bool) {
	s = strings.TrimSpace(s)
	parts := strings.Split(s, ".")
	if len(parts) != 3 {
		return 0, 0, 0, false
	}
	var err error
	a, err = strconv.Atoi(parts[0])
	if err != nil {
		return 0, 0, 0, false
	}
	b, err = strconv.Atoi(parts[1])
	if err != nil {
		return 0, 0, 0, false
	}
	c, err = strconv.Atoi(parts[2])
	if err != nil {
		return 0, 0, 0, false
	}
	return a, b, c, true
}

// semverDesc compares i and j: returns true if i should sort before j (higher version first)
func semverDesc(verI, verJ string) bool {
	ai, bi, ci, okI := parseSemverThree(verI)
	aj, bj, cj, okJ := parseSemverThree(verJ)
	if !okI && !okJ {
		return verI > verJ
	}
	if !okI {
		return false
	}
	if !okJ {
		return true
	}
	if ai != aj {
		return ai > aj
	}
	if bi != bj {
		return bi > bj
	}
	return ci > cj
}

func appReleasePublicBase(c *gin.Context) string {
	cfg := config.Load()
	if b := strings.TrimSpace(cfg.APIPublicBase); b != "" {
		return strings.TrimRight(b, "/")
	}
	scheme := "http"
	if xf := c.GetHeader("X-Forwarded-Proto"); strings.EqualFold(xf, "https") {
		scheme = "https"
	} else if c.Request.TLS != nil {
		scheme = "https"
	}
	host := c.Request.Host
	if h := c.GetHeader("X-Forwarded-Host"); strings.TrimSpace(h) != "" {
		host = strings.Split(strings.TrimSpace(h), ",")[0]
		host = strings.TrimSpace(host)
	}
	if host == "" {
		return ""
	}
	return scheme + "://" + host
}

func normalizeWebPath(p string) string {
	p = strings.TrimSpace(p)
	p = strings.ReplaceAll(p, "\\", "/")
	if p == "" {
		return ""
	}
	if !strings.HasPrefix(p, "/") {
		return "/" + p
	}
	return p
}

// UploadAppReleaseFile 管理端：APK / IPA / plist，≤50MB
func UploadAppReleaseFile(c *gin.Context) {
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		utils.BadRequest(c, "请选择文件")
		return
	}
	defer file.Close()

	if header.Size > appReleaseMaxUploadBytes {
		utils.BadRequest(c, "文件超过 50MB")
		return
	}

	limited := io.LimitReader(file, appReleaseMaxUploadBytes+1)
	data, err := io.ReadAll(limited)
	if err != nil {
		utils.ServerError(c, "读取文件失败")
		return
	}
	if int64(len(data)) > appReleaseMaxUploadBytes {
		utils.BadRequest(c, "文件超过 50MB")
		return
	}

	safe := strings.ToLower(filepath.Base(header.Filename))
	ext := filepath.Ext(safe)
	if ext != ".apk" && ext != ".ipa" && ext != ".plist" {
		utils.BadRequest(c, "仅支持 .apk .ipa .plist")
		return
	}

	dateDir := time.Now().Format("20060102")
	uploadDir := filepath.Join("./uploads/app-releases", dateDir)
	if err := os.MkdirAll(uploadDir, 0755); err != nil {
		utils.ServerError(c, "创建目录失败")
		return
	}

	filename := fmt.Sprintf("%d%s", time.Now().UnixNano(), ext)
	dst := filepath.Join(uploadDir, filename)
	if err := os.WriteFile(dst, data, 0644); err != nil {
		utils.ServerError(c, "保存文件失败")
		return
	}

	webPath := "/uploads/app-releases/" + dateDir + "/" + filename
	utils.Success(c, gin.H{
		"path":     webPath,
		"filename": filename,
		"size":     len(data),
	})
}

type appReleaseCreateRequest struct {
	Name          string `json:"name" binding:"required"`
	Platform      string `json:"platform" binding:"required"`
	Version       string `json:"version" binding:"required"`
	ForceUpdate   bool   `json:"force_update"`
	ReleaseNotes  string `json:"release_notes"`
	ApkPath       string `json:"apk_path"`
	IpaPath       string `json:"ipa_path"`
	ManifestPath  string `json:"manifest_path"`
}

func validateAppReleasePaths(platform, apk, ipa, manifest string) error {
	platform = strings.ToLower(strings.TrimSpace(platform))
	switch platform {
	case "android":
		if strings.TrimSpace(apk) == "" {
			return fmt.Errorf("请上传 APK")
		}
	case "ios":
		if strings.TrimSpace(ipa) == "" || strings.TrimSpace(manifest) == "" {
			return fmt.Errorf("请上传 IPA 与 manifest.plist")
		}
	default:
		return fmt.Errorf("端别无效")
	}
	return nil
}

// AdminListAppReleasePackages 管理端列表（启用在前，id 降序）
func AdminListAppReleasePackages(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "10"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 10
	}

	var total int64
	database.DB.Model(&models.AppReleasePackage{}).Count(&total)

	var list []models.AppReleasePackage
	database.DB.Model(&models.AppReleasePackage{}).
		Order("enabled DESC, id DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&list)

	utils.Success(c, gin.H{
		"list":       list,
		"total":      total,
		"page":       page,
		"page_size":  pageSize,
	})
}

// AdminCreateAppReleasePackage 新增（默认禁用，不可在此设启用）
func AdminCreateAppReleasePackage(c *gin.Context) {
	var req appReleaseCreateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	req.Platform = strings.ToLower(strings.TrimSpace(req.Platform))
	if req.Platform != "android" && req.Platform != "ios" {
		utils.BadRequest(c, "端别必须为 android 或 ios")
		return
	}
	req.Version = strings.TrimSpace(req.Version)
	if !validAppReleaseVersion(req.Version) {
		utils.BadRequest(c, "版本须为 x.y.z 三位数字")
		return
	}
	req.ApkPath = normalizeWebPath(req.ApkPath)
	req.IpaPath = normalizeWebPath(req.IpaPath)
	req.ManifestPath = normalizeWebPath(req.ManifestPath)

	if err := validateAppReleasePaths(req.Platform, req.ApkPath, req.IpaPath, req.ManifestPath); err != nil {
		utils.BadRequest(c, err.Error())
		return
	}

	pkg := models.AppReleasePackage{
		Name:         strings.TrimSpace(req.Name),
		Platform:     req.Platform,
		Version:      req.Version,
		Enabled:      false,
		ForceUpdate:  req.ForceUpdate,
		ReleaseNotes: req.ReleaseNotes,
		ApkPath:      req.ApkPath,
		IpaPath:      req.IpaPath,
		ManifestPath: req.ManifestPath,
	}
	if pkg.Platform == "android" {
		pkg.IpaPath, pkg.ManifestPath = "", ""
	} else {
		pkg.ApkPath = ""
	}

	if err := database.DB.Create(&pkg).Error; err != nil {
		utils.ServerError(c, "创建失败")
		return
	}
	utils.Success(c, pkg)
}

// AdminUpdateAppReleasePackage 编辑（不可改 enabled）
func AdminUpdateAppReleasePackage(c *gin.Context) {
	id := c.Param("id")
	var pkg models.AppReleasePackage
	if err := database.DB.First(&pkg, id).Error; err != nil {
		utils.BadRequest(c, "记录不存在")
		return
	}

	var req appReleaseCreateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	req.Platform = strings.ToLower(strings.TrimSpace(req.Platform))
	if req.Platform != "android" && req.Platform != "ios" {
		utils.BadRequest(c, "端别必须为 android 或 ios")
		return
	}
	req.Version = strings.TrimSpace(req.Version)
	if !validAppReleaseVersion(req.Version) {
		utils.BadRequest(c, "版本须为 x.y.z 三位数字")
		return
	}

	apk := normalizeWebPath(req.ApkPath)
	ipa := normalizeWebPath(req.IpaPath)
	man := normalizeWebPath(req.ManifestPath)
	if apk == "" {
		apk = pkg.ApkPath
	}
	if ipa == "" {
		ipa = pkg.IpaPath
	}
	if man == "" {
		man = pkg.ManifestPath
	}

	if err := validateAppReleasePaths(req.Platform, apk, ipa, man); err != nil {
		utils.BadRequest(c, err.Error())
		return
	}

	pkg.Name = strings.TrimSpace(req.Name)
	pkg.Platform = req.Platform
	pkg.Version = req.Version
	pkg.ForceUpdate = req.ForceUpdate
	pkg.ReleaseNotes = req.ReleaseNotes

	if pkg.Platform == "android" {
		pkg.ApkPath = apk
		pkg.IpaPath, pkg.ManifestPath = "", ""
	} else {
		pkg.IpaPath = ipa
		pkg.ManifestPath = man
		pkg.ApkPath = ""
	}

	if err := database.DB.Save(&pkg).Error; err != nil {
		utils.ServerError(c, "保存失败")
		return
	}
	utils.Success(c, pkg)
}

type appReleaseSetEnabledRequest struct {
	Enabled bool `json:"enabled"`
}

// AdminSetAppReleasePackageEnabled 列表切换启用/禁用
func AdminSetAppReleasePackageEnabled(c *gin.Context) {
	id := c.Param("id")
	var pkg models.AppReleasePackage
	if err := database.DB.First(&pkg, id).Error; err != nil {
		utils.BadRequest(c, "记录不存在")
		return
	}

	var req appReleaseSetEnabledRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}

	if !req.Enabled {
		pkg.Enabled = false
		database.DB.Save(&pkg)
		utils.Success(c, pkg)
		return
	}

	var conflict models.AppReleasePackage
	err := database.DB.Where("platform = ? AND enabled = ? AND id <> ?", pkg.Platform, true, pkg.ID).
		First(&conflict).Error
	if err == nil {
		msg := fmt.Sprintf("已有启用记录：名称「%s」、端别「%s」、版本「%s」、id「%d」",
			conflict.Name, conflict.Platform, conflict.Version, conflict.ID)
		c.JSON(http.StatusConflict, utils.Response{Code: 409, Message: msg, Data: nil})
		return
	}
	if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
		utils.ServerError(c, "检查启用冲突失败")
		return
	}

	pkg.Enabled = true
	database.DB.Save(&pkg)
	utils.Success(c, pkg)
}

// AdminDeleteAppReleasePackage 软删
func AdminDeleteAppReleasePackage(c *gin.Context) {
	id := c.Param("id")
	database.DB.Delete(&models.AppReleasePackage{}, id)
	utils.Success(c, nil)
}

// AppReleaseCheck 公开：版本更新检查（非法 platform → 200 data null）
func AppReleaseCheck(c *gin.Context) {
	platform := strings.ToLower(strings.TrimSpace(c.Query("platform")))
	if platform != "android" && platform != "ios" {
		log.Printf("[app-release-check] invalid platform %q", c.Query("platform"))
		utils.Success(c, nil)
		return
	}

	var rows []models.AppReleasePackage
	database.DB.Where("platform = ? AND enabled = ?", platform, true).Find(&rows)
	if len(rows) == 0 {
		utils.Success(c, nil)
		return
	}

	best := rows[0]
	for i := 1; i < len(rows); i++ {
		r := rows[i]
		if semverDesc(r.Version, best.Version) {
			best = r
		} else if r.Version == best.Version && r.UpdatedAt.After(best.UpdatedAt) {
			best = r
		}
	}
	if len(rows) > 1 {
		log.Printf("[app-release-check] multiple enabled for %s, picked id=%d version=%s", platform, best.ID, best.Version)
	}

	base := appReleasePublicBase(c)
	if base == "" {
		log.Printf("[app-release-check] empty public base for host=%s", c.Request.Host)
		utils.Success(c, nil)
		return
	}

	out := gin.H{
		"version":        best.Version,
		"force_update":   best.ForceUpdate,
		"release_notes":  best.ReleaseNotes,
	}
	if platform == "android" {
		apk := normalizeWebPath(best.ApkPath)
		if apk == "" {
			utils.Success(c, nil)
			return
		}
		out["download_url"] = base + apk
		utils.Success(c, out)
		return
	}

	man := normalizeWebPath(best.ManifestPath)
	if man == "" {
		utils.Success(c, nil)
		return
	}
	manifestURL := base + man
	itms := "itms-services://?action=download-manifest&url=" + url.QueryEscape(manifestURL)
	out["install_url"] = itms
	utils.Success(c, out)
}
