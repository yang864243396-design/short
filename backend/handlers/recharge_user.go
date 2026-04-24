package handlers

import (
	"errors"
	"fmt"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/payment/lbzf"
	"short-drama-backend/utils"
	"short-drama-backend/wallet"
)

// ListRechargePackages 用户可见的充值档位 + 支付方式
func ListRechargePackages(c *gin.Context) {
	var list []models.RechargePackage
	database.DB.Where("enabled = ?", true).Order("sort ASC, id ASC").Find(&list)
	var payOpts []models.PayProductConfig
	database.DB.Where("enabled = ?", true).Order("sort ASC, id ASC").Find(&payOpts)
	cfg := config.Load()
	utils.Success(c, gin.H{
		"list":            list,
		"pay_options":     payOpts,
		"lubzf_enabled":   cfg.LubzfEnabled,
		"currency_name":   wallet.CurrencyName,
		"coins_per_yuan":  wallet.CoinsPerYuan,
	})
}

// CreateRechargeOrder 无 LUBZF：兼容旧版即时到账。开启 LUBZF：pending + 调聚合下单返回 pay_url
func CreateRechargeOrder(c *gin.Context) {
	userID := c.GetUint("user_id")
	cfg := config.Load()

	var req struct {
		PackageID uint   `json:"package_id" binding:"required"`
		ProductID string `json:"product_id"` // LUBZF 时必填
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	var pkg models.RechargePackage
	if err := database.DB.Where("id = ? AND enabled = ?", req.PackageID, true).First(&pkg).Error; err != nil {
		utils.BadRequest(c, "套餐不存在或已下架")
		return
	}
	totalCoins := pkg.Coins + pkg.BonusCoins
	if pkg.Coins <= 0 || totalCoins <= 0 {
		utils.BadRequest(c, "套餐配置无效")
		return
	}
	amountFen := yuanToFen(pkg.PriceYuan)
	if amountFen < 1 {
		utils.BadRequest(c, "套餐价格无效")
		return
	}

	// 未开启聚合：与旧版一致
	if !cfg.LubzfEnabled {
		createRechargeOrderLegacy(c, userID, &pkg, totalCoins)
		return
	}
	if req.ProductID == "" {
		utils.BadRequest(c, "请选择支付方式（product_id）")
		return
	}
	if cfg.LubzfMchID == "" || cfg.LubzfSignKey == "" {
		utils.ServerError(c, "聚合支付未正确配置：请设置 LUBZF_MCH_ID 与 LUBZF_SIGN_KEY")
		return
	}
	prod4 := padProductID4(req.ProductID)
	var payCfg models.PayProductConfig
	if err := database.DB.Where("product_id = ? AND enabled = ?", prod4, true).First(&payCfg).Error; err != nil {
		utils.BadRequest(c, "支付方式无效或已停用")
		return
	}

	var payURL, payOrderID string
	var order models.RechargeOrder
	err := database.DB.Transaction(func(tx *gorm.DB) error {
		n, e := todayPendingRechargeCount(tx, userID)
		if e != nil {
			return e
		}
		if n >= 100 {
			return errPendingCap
		}
		mch, e2 := allocMchOrderNo(tx, userID, prod4)
		if e2 != nil {
			return e2
		}
		mchStr := mch
		subj := subjectSnapFromPackage(pkg.Name)
		order = models.RechargeOrder{
			UserID:      userID,
			PackageID:   pkg.ID,
			Coins:       totalCoins,
			PriceYuan:   pkg.PriceYuan,
			Status:      models.RechargeOrderPending,
			MchOrderNo:  &mchStr,
			ProductID:   prod4,
			AmountFen:   amountFen,
			SubjectSnap: subj,
		}
		return tx.Create(&order).Error
	})
	if err != nil {
		if errors.Is(err, errPendingCap) {
			utils.BadRequest(c, "当日在途未支付订单过多，请稍后再试")
			return
		}
		utils.ServerError(c, "创建订单失败")
		return
	}
	_ = database.DB.First(&order, order.ID)
	mch := ""
	if order.MchOrderNo != nil {
		mch = *order.MchOrderNo
	}
	subj := order.SubjectSnap
	base := publicAPIBase(c, cfg.APIPublicBase)
	if base == "" {
		utils.ServerError(c, "无法确定回调根地址：请设置 API_PUBLIC_BASE，或通过浏览器/App 以公网可访问的域名访问本 API（反代需传 X-Forwarded-Proto/Host）")
		return
	}
	notify := base + "/api/v1/pay/lbzf/notify"
	payURL, payOrderID, err = lbzf.CreateOrder(cfg.LubzfAPIBase, cfg.LubzfMchID, cfg.LubzfSignKey, notify, subj, subj, "-", mch, prod4, amountFen)
	if err != nil {
		now := time.Now()
		_ = database.DB.Model(&order).Updates(map[string]any{
			"status":       models.RechargeOrderCancelled,
			"cancelled_at": now,
			"updated_at":   now,
		})
		payNotifyPrintf(cfg, "[pay/lbzf/create_order] user_id=%d order_id=%d mch=%s package_id=%d product_id=%s result=fail err=%v",
			userID, order.ID, mch, pkg.ID, prod4, err)
		utils.BadRequest(c, "创建支付失败，请稍后重试")
		return
	}
	if payOrderID != "" {
		_ = database.DB.Model(&order).Update("pay_order_id", payOrderID)
	}
	_ = database.DB.First(&order, order.ID)
	utils.Success(c, gin.H{
		"order":         order,
		"pay_url":       payURL,
		"coins":         scanAppUserCoins(userID),
		"mch_order_no":  order.MchOrderNo,
	})
}

var errPendingCap = errors.New("pending cap")

func startOfLocalDay() time.Time {
	now := time.Now().In(time.Local)
	y, m, d := now.Date()
	return time.Date(y, m, d, 0, 0, 0, 0, now.Location())
}

func createRechargeOrderLegacy(c *gin.Context, userID uint, pkg *models.RechargePackage, totalCoins int) {
	var order models.RechargeOrder
	var coinsAfter int
	err := database.DB.Transaction(func(tx *gorm.DB) error {
		order = models.RechargeOrder{
			UserID:     userID,
			PackageID:  pkg.ID,
			Coins:      totalCoins,
			PriceYuan:  pkg.PriceYuan,
			Status:     models.RechargeOrderPending,
			AmountFen:  yuanToFen(pkg.PriceYuan),
			SubjectSnap: subjectSnapFromPackage(pkg.Name),
		}
		if err := tx.Create(&order).Error; err != nil {
			return err
		}
		title := "充值金币"
		remark := fmt.Sprintf("订单#%d·套餐#%d", order.ID, order.PackageID)
		if pkg.BonusCoins > 0 {
			remark += fmt.Sprintf("（含赠送%d）", pkg.BonusCoins)
		}
		bal, werr := wallet.Apply(tx, userID, models.WalletTxRecharge, order.Coins, title, remark, models.WalletRefRechargeOrder, order.ID, 0, "")
		if werr != nil {
			return werr
		}
		coinsAfter = bal
		now := time.Now()
		order.Status = models.RechargeOrderPaid
		order.PaidAt = &now
		return tx.Save(&order).Error
	})
	if err != nil {
		if errors.Is(err, wallet.ErrAccountDeleted) {
			utils.BadRequest(c, "账号已注销")
			return
		}
		utils.ServerError(c, "充值失败")
		return
	}
	utils.Success(c, gin.H{
		"order":  order,
		"coins":  coinsAfter,
		"pay_url": nil,
	})
}

// SimulateRechargeOrderComplete 仅 USER_RECHARGE_SIMULATE
func SimulateRechargeOrderComplete(c *gin.Context) {
	cfg := config.Load()
	if !cfg.UserRechargeSimulate {
		utils.BadRequest(c, "未开启模拟支付")
		return
	}
	userID := c.GetUint("user_id")
	oid, err := strconv.ParseUint(c.Param("id"), 10, 32)
	if err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	err = database.DB.Transaction(func(tx *gorm.DB) error {
		var order models.RechargeOrder
		if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).First(&order, uint(oid)).Error; err != nil {
			return err
		}
		if order.UserID != userID {
			return errors.New("forbidden")
		}
		if order.Status == models.RechargeOrderPaid {
			return nil
		}
		if order.Status != models.RechargeOrderPending {
			return errors.New("bad_status")
		}
		mch := ""
		if order.MchOrderNo != nil {
			mch = *order.MchOrderNo
		}
		title := "充值金币"
		remark := fmt.Sprintf("订单#%d·套餐#%d", order.ID, order.PackageID)
		if _, werr := wallet.Apply(tx, userID, models.WalletTxRecharge, order.Coins, title, remark, models.WalletRefRechargeOrder, order.ID, 0, mch); werr != nil {
			return werr
		}
		now := time.Now()
		order.Status = models.RechargeOrderPaid
		order.PaidAt = &now
		return tx.Save(&order).Error
	})
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			utils.BadRequest(c, "订单不存在")
			return
		}
		if err.Error() == "forbidden" {
			utils.BadRequest(c, "无权操作")
			return
		}
		if err.Error() == "bad_status" {
			utils.BadRequest(c, "订单状态不可支付")
			return
		}
		if errors.Is(err, wallet.ErrAccountDeleted) {
			utils.BadRequest(c, "账号已注销")
			return
		}
		utils.ServerError(c, "入账失败")
		return
	}
	var u models.AppUser
	database.DB.Select("id", "coins").Where("id = ? AND deleted_at IS NULL", userID).First(&u)
	utils.Success(c, gin.H{"message": "支付成功", "coins": u.Coins})
}
