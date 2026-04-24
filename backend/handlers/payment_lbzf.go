package handlers

import (
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/payment/lbzf"
	"short-drama-backend/utils"
	"gorm.io/gorm"
)

func notifyClientIPInWhitelist(allowed []string, clientIP string) bool {
	if len(allowed) == 0 {
		return true
	}
	for _, a := range allowed {
		if a == clientIP {
			return true
		}
	}
	return false
}

// LubzfNotify 聚合异步通知（无 JWT）
func LubzfNotify(c *gin.Context) {
	clientIP := c.ClientIP()
	cfg := config.Load()
	if !cfg.LubzfEnabled {
		payNotifyPrintf(cfg, "[pay/lbzf/notify] ip=%s result=body_success reason=lubzf_disabled", clientIP)
		c.String(200, "success")
		return
	}
	allowList := cfg.LubzfNotifyIPWhitelist()
	if !notifyClientIPInWhitelist(allowList, clientIP) {
		payNotifyPrintf(cfg, "[pay/lbzf/notify] ip=%s result=fail reason=ip_not_whitelist allowed=%q", clientIP, allowList)
		c.String(200, "fail")
		return
	}
	if err := c.Request.ParseForm(); err != nil {
		payNotifyPrintf(cfg, "[pay/lbzf/notify] ip=%s result=fail reason=parse_form err=%v", clientIP, err)
		c.String(200, "fail")
		return
	}
	pairs := lbzf.FormToPairs(c.Request.PostForm)
	if !lbzf.Verify(pairs, cfg.LubzfSignKey) {
		payNotifyPrintf(cfg, "[pay/lbzf/notify] ip=%s result=fail reason=verify_sign mchOrderNo=%q", clientIP, pairs["mchOrderNo"])
		c.String(200, "fail")
		return
	}
	mch := strings.TrimSpace(pairs["mchOrderNo"])
	amount, _ := strconv.Atoi(pairs["amount"])
	st, _ := strconv.Atoi(pairs["status"])
	payOID := strings.TrimSpace(pairs["payOrderId"])
	if mch == "" {
		payNotifyPrintf(cfg, "[pay/lbzf/notify] ip=%s result=fail reason=empty_mchOrderNo", clientIP)
		c.String(200, "fail")
		return
	}
	err := database.DB.Transaction(func(tx *gorm.DB) error {
		_, _, e := tryCreditRechargeFromGateway(tx, mch, payOID, amount, st)
		return e
	})
	if err != nil {
		payNotifyPrintf(cfg, "[pay/lbzf/notify] ip=%s mchOrderNo=%s payOrderId=%s amount=%d status=%d result=fail reason=tx err=%v",
			clientIP, mch, payOID, amount, st, err)
		c.String(200, "fail")
		return
	}
	payNotifyPrintf(cfg, "[pay/lbzf/notify] ip=%s mchOrderNo=%s payOrderId=%s amount=%d status=%d result=success", clientIP, mch, payOID, amount, st)
	c.String(200, "success")
}

// QueryLubzfRechargeOrder 用户主动查单并尝试入账
// 必传其一：mch_order_no（我方商户单号）或 pay_order_id（聚合/台方支付单号）。台方后台常只展示 pay_order_id，与 mch 不一致时可只传 pay_order_id。
func QueryLubzfRechargeOrder(c *gin.Context) {
	userID := c.GetUint("user_id")
	mchQ := strings.TrimSpace(c.Query("mch_order_no"))
	payQ := strings.TrimSpace(c.Query("pay_order_id"))
	if mchQ == "" && payQ == "" {
		utils.BadRequest(c, "请提供 mch_order_no 或 pay_order_id")
		return
	}
	var o models.RechargeOrder
	q := database.DB.Where("user_id = ?", userID)
	if mchQ != "" && payQ != "" {
		q = q.Where("(mch_order_no = ? OR pay_order_id = ?)", mchQ, payQ)
	} else if mchQ != "" {
		q = q.Where("mch_order_no = ?", mchQ)
	} else {
		q = q.Where("pay_order_id = ?", payQ)
	}
	if err := q.First(&o).Error; err != nil {
		utils.BadRequest(c, "订单不存在")
		return
	}
	_ = database.DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.First(&o, o.ID).Error; err != nil {
			return err
		}
		maybeLazyCancelRecharge(tx, &o)
		return nil
	})
	_ = database.DB.First(&o, o.ID)

	cfg := config.Load()
	if !cfg.LubzfEnabled || o.MchOrderNo == nil || *o.MchOrderNo == "" {
		utils.Success(c, gin.H{"order": o})
		return
	}
	mchKey := *o.MchOrderNo
	amount, st, payOID, err := lbzf.QueryOrder(cfg.LubzfAPIBase, cfg.LubzfMchID, cfg.LubzfSignKey, mchKey, "")
	if err != nil {
		// 库内已有订单，但渠道查单失败（超时、 retCode 非 SUCCESS、网络等）：仍返回 200 + 当前库内状态，
		// 让客户端走「待支付/已支付」等提示，而勿误报「服务繁忙」；运维可查 pay_notify 日志。
		payNotifyPrintf(cfg, "[pay/lbzf/query] user_id=%d mch=%s result=degraded err=%v", userID, mchKey, err)
		replyLubzfQueryOrderOK(c, userID, o.ID)
		return
	}
	err = database.DB.Transaction(func(tx *gorm.DB) error {
		_, _, e := tryCreditRechargeFromGateway(tx, mchKey, payOID, amount, st)
		return e
	})
	if err != nil {
		payNotifyPrintf(cfg, "[pay/lbzf/query] user_id=%d mch=%s payOrderId=%s amount=%d status=%d result=fail reason=credit err=%v",
			userID, mchKey, payOID, amount, st, err)
		utils.ServerError(c, "入账处理失败")
		return
	}
	replyLubzfQueryOrderOK(c, userID, o.ID)
}

// replyLubzfQueryOrderOK 与原先两次分支结尾一致：按 orderID 重载订单并带上用户金币
func replyLubzfQueryOrderOK(c *gin.Context, userID uint, orderID uint) {
	var o models.RechargeOrder
	_ = database.DB.First(&o, orderID)
	utils.Success(c, gin.H{"order": o, "coins": scanAppUserCoins(userID)})
}
