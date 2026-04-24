package handlers

import (
	"errors"
	"fmt"
	"time"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
	"short-drama-backend/wallet"
)

// configToJSON 单条配置输出（用户端列表）
func adSkipConfigJSON(cfg models.AdSkipConfig) gin.H {
	h := gin.H{
		"id":             cfg.ID,
		"name":           cfg.Name,
		"package_type":   cfg.PackageType,
		"duration_hours": cfg.DurationHours,
		"skip_count":     cfg.SkipCount,
		"price_coins":    cfg.PriceCoins,
		"sort":           cfg.Sort,
	}
	if cfg.PackageType == "" {
		h["package_type"] = models.AdSkipPackageTypeTime
	}
	return h
}

// GetAdSkipStatus 当前用户免广告权益与可购买档位
func GetAdSkipStatus(c *gin.Context) {
	userID := c.GetUint("user_id")
	now := time.Now()

	var u models.AppUser
	database.DB.Select("ad_skip_expires_at", "ad_skip_remaining", "coins").Where("id = ? AND deleted_at IS NULL", userID).First(&u)
	if u.AdSkipExpiresAt != nil && (u.AdSkipExpiresAt.IsZero() || !u.AdSkipExpiresAt.After(now)) && u.AdSkipRemaining > 0 {
		_ = database.DB.Model(&models.AppUser{}).Where("id = ?", userID).Update("ad_skip_remaining", 0).Error
		u.AdSkipRemaining = 0
	}

	var list []models.AdSkipConfig
	database.DB.Where("enabled = ?", true).Order("sort ASC, id ASC").Find(&list)
	timeConfigs := make([]gin.H, 0)
	boosterConfigs := make([]gin.H, 0)
	for _, cfg := range list {
		if cfg.PackageType == models.AdSkipPackageTypeBooster {
			if cfg.SkipCount < 1 || cfg.PriceCoins < 0 {
				continue
			}
			boosterConfigs = append(boosterConfigs, adSkipConfigJSON(cfg))
			continue
		}
		if cfg.DurationHours < 1 {
			continue
		}
		if cfg.SkipCount < 1 {
			continue
		}
		if cfg.PriceCoins < 0 {
			continue
		}
		timeConfigs = append(timeConfigs, adSkipConfigJSON(cfg))
	}

	active := u.AdSkipExpiresAt != nil && u.AdSkipExpiresAt.After(now)
	// configs：当前用户「下一步可购」的档位。权益在期时一律为加油包，避免客户端只读 configs 时仍看到时间包且无法与 booster_configs 同步。
	configsForClient := timeConfigs
	if active {
		if len(boosterConfigs) > 0 {
			configsForClient = boosterConfigs
		} else {
			configsForClient = []gin.H{}
		}
	}
	utils.Success(c, gin.H{
		"configs":            configsForClient,
		"time_configs":       timeConfigs,
		"booster_configs":    boosterConfigs,
		"ad_skip_active":     active,
		"ad_skip_expires_at": u.AdSkipExpiresAt,
		"ad_skip_remaining":  u.AdSkipRemaining,
		"coins":              u.Coins,
		"currency_name":      wallet.CurrencyName,
	})
}

// PurchaseAdSkip 按档位 ID 购买时间包或加油包
func PurchaseAdSkip(c *gin.Context) {
	userID := c.GetUint("user_id")
	var req struct {
		ConfigID uint `json:"config_id" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil || req.ConfigID == 0 {
		utils.BadRequest(c, "请选择购买档位")
		return
	}
	err := database.DB.Transaction(func(tx *gorm.DB) error {
		var cfg models.AdSkipConfig
		if err := tx.First(&cfg, req.ConfigID).Error; err != nil {
			return err
		}
		if !cfg.Enabled {
			return errors.New("disabled")
		}
		if cfg.PriceCoins < 0 {
			return errors.New("disabled")
		}
		isBooster := cfg.PackageType == models.AdSkipPackageTypeBooster
		skipN := cfg.SkipCount
		if skipN < 1 {
			skipN = 100
		}
		var u models.AppUser
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("id = ? AND deleted_at IS NULL", userID).First(&u).Error; err != nil {
			return err
		}
		now := time.Now()
		active := u.AdSkipExpiresAt != nil && u.AdSkipExpiresAt.After(now)
		remark := fmt.Sprintf("购买免广告时间包·%s", cfg.Name)
		if isBooster {
			if cfg.DurationHours != 0 {
				return errors.New("cfg_invalid")
			}
			if !active {
				return errors.New("no_time")
			}
			remark = fmt.Sprintf("购买免广告加油包·%s", cfg.Name)
		} else {
			if cfg.DurationHours < 1 || cfg.DurationHours > 8760 {
				return errors.New("cfg_invalid")
			}
			if active {
				return errors.New("overlap")
			}
		}
		_, werr := wallet.Apply(tx, userID, models.WalletTxConsume, cfg.PriceCoins, remark, "", models.WalletRefAdSkipPurchase, cfg.ID, 0, "")
		if werr != nil {
			return werr
		}
		if isBooster {
			return tx.Model(&models.AppUser{}).Where("id = ?", userID).
				UpdateColumn("ad_skip_remaining", gorm.Expr("ad_skip_remaining + ?", skipN)).Error
		}
		newExp := time.Now().Add(time.Duration(cfg.DurationHours) * time.Hour)
		return tx.Model(&models.AppUser{}).Where("id = ?", userID).Updates(map[string]interface{}{
			"ad_skip_expires_at": newExp,
			"ad_skip_remaining":  skipN,
		}).Error
	})
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			utils.BadRequest(c, "档位不存在或已删除")
			return
		}
		if err.Error() == "disabled" {
			utils.BadRequest(c, "跳过卡暂未开放")
			return
		}
		if err.Error() == "no_time" {
			utils.BadRequest(c, "需先购买时间包")
			return
		}
		if err.Error() == "overlap" {
			utils.BadRequest(c, "当前时间包未到期，不能重复购买时间包")
			return
		}
		if err.Error() == "cfg_invalid" {
			utils.BadRequest(c, "档位配置无效")
			return
		}
		if errors.Is(err, wallet.ErrInsufficient) {
			utils.BadRequest(c, "金币不足")
			return
		}
		utils.ServerError(c, "购买失败")
		return
	}
	var u models.AppUser
	database.DB.Select("coins", "ad_skip_expires_at", "ad_skip_remaining").Where("id = ? AND deleted_at IS NULL", userID).First(&u)
	utils.Success(c, gin.H{
		"ad_skip_expires_at":  u.AdSkipExpiresAt,
		"ad_skip_remaining":   u.AdSkipRemaining,
		"coins":               u.Coins,
		"message":             "购买成功",
	})
}
