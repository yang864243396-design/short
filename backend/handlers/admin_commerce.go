package handlers

import (
	"strconv"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

// --- 充值套餐（管理端；前端路由权限 recharge-packages，与 wallet 分离）---

func AdminListRechargePackages(c *gin.Context) {
	var list []models.RechargePackage
	database.DB.Order("sort ASC, id ASC").Find(&list)
	utils.Success(c, gin.H{"list": list})
}

func AdminCreateRechargePackage(c *gin.Context) {
	var row models.RechargePackage
	if err := c.ShouldBindJSON(&row); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	if row.Coins <= 0 || row.Name == "" {
		utils.BadRequest(c, "名称与金币数必填")
		return
	}
	if row.BonusCoins < 0 {
		utils.BadRequest(c, "赠送金币不能为负数")
		return
	}
	database.DB.Create(&row)
	utils.Success(c, row)
}

func AdminUpdateRechargePackage(c *gin.Context) {
	id := c.Param("id")
	var row models.RechargePackage
	if err := database.DB.First(&row, id).Error; err != nil {
		utils.BadRequest(c, "记录不存在")
		return
	}
	var body models.RechargePackage
	if err := c.ShouldBindJSON(&body); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	body.ID = row.ID
	if body.Name == "" || body.Coins <= 0 {
		utils.BadRequest(c, "名称与金币数无效")
		return
	}
	if body.BonusCoins < 0 {
		utils.BadRequest(c, "赠送金币不能为负数")
		return
	}
	database.DB.Save(&body)
	utils.Success(c, body)
}

func AdminDeleteRechargePackage(c *gin.Context) {
	id := c.Param("id")
	database.DB.Delete(&models.RechargePackage{}, id)
	utils.Success(c, nil)
}

// --- 广告跳过卡档位（多行 CRUD，建议角色含 ads）---

func AdminListAdSkipConfigs(c *gin.Context) {
	var list []models.AdSkipConfig
	database.DB.Order("sort ASC, id ASC").Find(&list)
	utils.Success(c, gin.H{"list": list})
}

func AdminCreateAdSkipConfig(c *gin.Context) {
	var row models.AdSkipConfig
	if err := c.ShouldBindJSON(&row); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	if row.Name == "" {
		utils.BadRequest(c, "请填写档位名称")
		return
	}
	if row.PackageType == "" {
		row.PackageType = models.AdSkipPackageTypeTime
	}
	if row.PackageType == models.AdSkipPackageTypeBooster {
		row.DurationHours = 0
		if row.SkipCount < 1 {
			row.SkipCount = 100
		}
		if row.PriceCoins < 0 {
			utils.BadRequest(c, "价格不能为负数")
			return
		}
	} else {
		if row.DurationHours < 1 || row.DurationHours > 8760 {
			utils.BadRequest(c, "时长需在 1～8760 小时之间")
			return
		}
		if row.SkipCount < 1 {
			row.SkipCount = 100
		}
		if row.PriceCoins < 0 {
			utils.BadRequest(c, "价格不能为负数")
			return
		}
	}
	database.DB.Create(&row)
	utils.Success(c, row)
}

func AdminUpdateAdSkipConfig(c *gin.Context) {
	id := c.Param("id")
	var row models.AdSkipConfig
	if err := database.DB.First(&row, id).Error; err != nil {
		utils.BadRequest(c, "记录不存在")
		return
	}
	var body models.AdSkipConfig
	if err := c.ShouldBindJSON(&body); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	if body.Name == "" {
		utils.BadRequest(c, "请填写档位名称")
		return
	}
	pt := body.PackageType
	if pt == "" {
		pt = models.AdSkipPackageTypeTime
	}
	if pt == models.AdSkipPackageTypeBooster {
		body.DurationHours = 0
		if body.SkipCount < 1 {
			body.SkipCount = 100
		}
		if body.PriceCoins < 0 {
			utils.BadRequest(c, "价格不能为负数")
			return
		}
	} else {
		if body.DurationHours < 1 || body.DurationHours > 8760 {
			utils.BadRequest(c, "时长需在 1～8760 小时之间")
			return
		}
		if body.SkipCount < 1 {
			body.SkipCount = 100
		}
		if body.PriceCoins < 0 {
			utils.BadRequest(c, "价格不能为负数")
			return
		}
	}
	row.Name = body.Name
	row.PackageType = pt
	row.DurationHours = body.DurationHours
	row.SkipCount = body.SkipCount
	row.PriceCoins = body.PriceCoins
	row.Enabled = body.Enabled
	row.Sort = body.Sort
	database.DB.Save(&row)
	utils.Success(c, row)
}

func AdminDeleteAdSkipConfig(c *gin.Context) {
	id := c.Param("id")
	database.DB.Delete(&models.AdSkipConfig{}, id)
	utils.Success(c, nil)
}

// AdminListUserRecentWalletTx 用户最近流水（管理端用户详情用）
func AdminListUserRecentWalletTx(c *gin.Context) {
	uidStr := c.Param("id")
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "10"))
	if pageSize < 1 {
		pageSize = 10
	}
	if pageSize > 50 {
		pageSize = 50
	}
	var list []AdminWalletTransactionItem
	database.DB.Table("wallet_transactions as t").
		Select("t.*, u.username as username").
		Joins("LEFT JOIN app_users u ON u.id = t.user_id").
		Where("t.user_id = ?", uidStr).
		Order("t.id DESC").
		Limit(pageSize).
		Scan(&list)
	utils.Success(c, gin.H{"list": list})
}
