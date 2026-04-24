package handlers

import (
	"errors"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"

	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
	"short-drama-backend/wallet"
)

const maxRechargeCoinsPerOp = 10_000_000

// GetWallet 当前用户金币余额与货币规则（1 元 = 100 金币，写死）
func GetWallet(c *gin.Context) {
	userID := c.GetUint("user_id")
	var u models.AppUser
	if err := database.DB.Select("id", "coins", "ad_skip_expires_at", "ad_skip_remaining").Where("id = ? AND deleted_at IS NULL", userID).First(&u).Error; err != nil {
		utils.BadRequest(c, "用户不存在")
		return
	}
	now := time.Now()
	if u.AdSkipExpiresAt != nil && (u.AdSkipExpiresAt.IsZero() || !u.AdSkipExpiresAt.After(now)) && u.AdSkipRemaining > 0 {
		_ = database.DB.Model(&models.AppUser{}).Where("id = ?", userID).Update("ad_skip_remaining", 0).Error
		u.AdSkipRemaining = 0
	}
	adSkipActive := u.AdSkipExpiresAt != nil && u.AdSkipExpiresAt.After(now)
	utils.Success(c, gin.H{
		"coins":                u.Coins,
		"currency_name":        wallet.CurrencyName,
		"coins_per_yuan":       wallet.CoinsPerYuan,
		"balance_yuan":         wallet.CoinsToYuan(u.Coins),
		"ad_skip_expires_at":   u.AdSkipExpiresAt,
		"ad_skip_active":       adSkipActive,
		"ad_skip_remaining":    u.AdSkipRemaining,
	})
}

// GetWalletTransactions 当前用户流水
func GetWalletTransactions(c *gin.Context) {
	userID := c.GetUint("user_id")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 {
		pageSize = 20
	}
	if pageSize > 50 {
		pageSize = 50
	}
	offset := (page - 1) * pageSize
	txType := c.Query("type")

	q := database.DB.Model(&models.WalletTransaction{}).Where("user_id = ?", userID).
		Where("(type = ? AND ref_type IN ?) OR (type = ? AND ref_type IN ?)",
			models.WalletTxRecharge,
			[]string{models.WalletRefRechargeOrder, models.WalletRefAdminRecharge},
			models.WalletTxConsume,
			[]string{models.WalletRefAdSkipPurchase, models.WalletRefEpisodeUnlock, models.WalletRefAdminDeduct},
		)
	if txType == models.WalletTxRecharge || txType == models.WalletTxConsume {
		q = q.Where("type = ?", txType)
	}
	var total int64
	q.Count(&total)

	var list []models.WalletTransaction
	q.Order("id DESC").Offset(offset).Limit(pageSize).Find(&list)

	utils.Success(c, gin.H{
		"list":       list,
		"total":      total,
		"page":       page,
		"page_size":  pageSize,
		"currency":   wallet.CurrencyName,
		"coins_per_yuan": wallet.CoinsPerYuan,
	})
}

// AdminRechargeUser 后台给用户充值金币（与人民币换算：1 元 = 100 金币）
func AdminRechargeUser(c *gin.Context) {
	targetIDStr := c.Param("id")
	targetUID64, err := strconv.ParseUint(targetIDStr, 10, 32)
	if err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	targetUID := uint(targetUID64)

	var req struct {
		Coins  int     `json:"coins"`
		Yuan   float64 `json:"yuan"`
		Remark string  `json:"remark"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}

	coins := req.Coins
	if coins <= 0 && req.Yuan > 0 {
		coins = wallet.YuanToCoins(req.Yuan)
	}
	if coins <= 0 {
		utils.BadRequest(c, "请填写充值金币数量或人民币金额")
		return
	}
	if coins > maxRechargeCoinsPerOp {
		utils.BadRequest(c, "单次充值金币数量过大")
		return
	}

	var u models.AppUser
	if err := database.DB.Where("id = ? AND deleted_at IS NULL", targetUID).First(&u).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			utils.BadRequest(c, "用户不存在或已注销")
			return
		}
		utils.ServerError(c, "查询失败")
		return
	}

	adminID := c.GetUint("user_id")
	title := "后台赠送"
	remark := req.Remark

	err = database.DB.Transaction(func(tx *gorm.DB) error {
		_, err := wallet.Apply(tx, targetUID, models.WalletTxRecharge, coins, title, remark, models.WalletRefAdminRecharge, 0, adminID, "")
		return err
	})
	if err != nil {
		if errors.Is(err, wallet.ErrInvalidAmount) {
			utils.BadRequest(c, "金币数量无效")
			return
		}
		if errors.Is(err, wallet.ErrAccountDeleted) {
			utils.BadRequest(c, "用户已注销")
			return
		}
		utils.ServerError(c, "充值失败")
		return
	}

	database.DB.Where("id = ? AND deleted_at IS NULL", targetUID).First(&u)
	utils.Success(c, gin.H{
		"user_id": targetUID,
		"coins":   u.Coins,
		"currency_name": wallet.CurrencyName,
	})
}

// AdminDeductUser 后台扣减用户金币
func AdminDeductUser(c *gin.Context) {
	targetIDStr := c.Param("id")
	targetUID64, err := strconv.ParseUint(targetIDStr, 10, 32)
	if err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	targetUID := uint(targetUID64)

	var req struct {
		Coins  int    `json:"coins"`
		Remark string `json:"remark"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	coins := req.Coins
	if coins <= 0 {
		utils.BadRequest(c, "请填写扣减金币数量")
		return
	}
	if coins > maxRechargeCoinsPerOp {
		utils.BadRequest(c, "单次扣减金币数量过大")
		return
	}

	var u models.AppUser
	if err := database.DB.Where("id = ? AND deleted_at IS NULL", targetUID).First(&u).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			utils.BadRequest(c, "用户不存在或已注销")
			return
		}
		utils.ServerError(c, "查询失败")
		return
	}

	adminID := c.GetUint("user_id")
	title := "后台扣除"
	remark := req.Remark

	err = database.DB.Transaction(func(tx *gorm.DB) error {
		_, err := wallet.Apply(tx, targetUID, models.WalletTxConsume, coins, title, remark, models.WalletRefAdminDeduct, 0, adminID, "")
		return err
	})
	if err != nil {
		if errors.Is(err, wallet.ErrInvalidAmount) {
			utils.BadRequest(c, "金币数量无效")
			return
		}
		if errors.Is(err, wallet.ErrInsufficient) {
			utils.BadRequest(c, "用户金币余额不足")
			return
		}
		if errors.Is(err, wallet.ErrAccountDeleted) {
			utils.BadRequest(c, "用户已注销")
			return
		}
		utils.ServerError(c, "扣减失败")
		return
	}

	database.DB.Where("id = ? AND deleted_at IS NULL", targetUID).First(&u)
	utils.Success(c, gin.H{
		"user_id":       targetUID,
		"coins":         u.Coins,
		"currency_name": wallet.CurrencyName,
	})
}

// AdminWalletTransactionItem 后台流水列表（含用户名）
type AdminWalletTransactionItem struct {
	models.WalletTransaction
	Username string `json:"username"`
}

// AdminListWalletTransactions 后台查询流水
func AdminListWalletTransactions(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 {
		pageSize = 20
	}
	if pageSize > 100 {
		pageSize = 100
	}
	offset := (page - 1) * pageSize
	userIDStr := c.Query("user_id")
	txType := c.Query("type")

	q := database.DB.Table("wallet_transactions as t").
		Select("t.*, u.username as username").
		Joins("LEFT JOIN app_users u ON u.id = t.user_id")
	if userIDStr != "" {
		q = q.Where("t.user_id = ?", userIDStr)
	}
	if txType == models.WalletTxRecharge || txType == models.WalletTxConsume {
		q = q.Where("t.type = ?", txType)
	}

	var total int64
	countQ := database.DB.Model(&models.WalletTransaction{})
	if userIDStr != "" {
		countQ = countQ.Where("user_id = ?", userIDStr)
	}
	if txType == models.WalletTxRecharge || txType == models.WalletTxConsume {
		countQ = countQ.Where("type = ?", txType)
	}
	countQ.Count(&total)

	var list []AdminWalletTransactionItem
	q.Order("t.id DESC").Offset(offset).Limit(pageSize).Scan(&list)

	utils.Success(c, gin.H{
		"list":            list,
		"total":           total,
		"page":            page,
		"page_size":       pageSize,
		"currency_name":   wallet.CurrencyName,
		"coins_per_yuan":  wallet.CoinsPerYuan,
	})
}
