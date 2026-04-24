package handlers

import (
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
	"short-drama-backend/wallet"
)

// AdminRechargeOrderRow 管理端列表（带用户名）
type AdminRechargeOrderRow struct {
	models.RechargeOrder
	Username string `json:"username"`
}

// AdminListRechargeOrders 充值订单列表（多条件查询）
// 查询：user_id, username（子串，app_users.username）, mch_order_no（子串）, pay_order_id, product_id, status
func AdminListRechargeOrders(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 {
		pageSize = 20
	}
	if pageSize > 200 {
		pageSize = 200
	}
	offset := (page - 1) * pageSize

	userID := strings.TrimSpace(c.Query("user_id"))
	usernameQ := strings.TrimSpace(c.Query("username"))
	mchQ := strings.TrimSpace(c.Query("mch_order_no"))
	payOID := strings.TrimSpace(c.Query("pay_order_id"))
	productID := strings.TrimSpace(c.Query("product_id"))
	st := strings.TrimSpace(c.Query("status"))

	q := database.DB.Table("recharge_orders as o").
		Select("o.*, u.username as username").
		Joins("LEFT JOIN app_users u ON u.id = o.user_id")
	if userID != "" {
		q = q.Where("o.user_id = ?", userID)
	}
	if usernameQ != "" {
		q = q.Where("u.username LIKE ?", "%"+usernameQ+"%")
	}
	if mchQ != "" {
		q = q.Where("o.mch_order_no LIKE ?", "%"+mchQ+"%")
	}
	if payOID != "" {
		q = q.Where("o.pay_order_id LIKE ?", "%"+payOID+"%")
	}
	if productID != "" {
		q = q.Where("o.product_id = ?", productID)
	}
	if st == models.RechargeOrderPending || st == models.RechargeOrderPaid || st == models.RechargeOrderCancelled {
		q = q.Where("o.status = ?", st)
	}

	var total int64
	countQ := database.DB.Table("recharge_orders as o").Joins("LEFT JOIN app_users u ON u.id = o.user_id")
	if userID != "" {
		countQ = countQ.Where("o.user_id = ?", userID)
	}
	if usernameQ != "" {
		countQ = countQ.Where("u.username LIKE ?", "%"+usernameQ+"%")
	}
	if mchQ != "" {
		countQ = countQ.Where("o.mch_order_no LIKE ?", "%"+mchQ+"%")
	}
	if payOID != "" {
		countQ = countQ.Where("o.pay_order_id LIKE ?", "%"+payOID+"%")
	}
	if productID != "" {
		countQ = countQ.Where("o.product_id = ?", productID)
	}
	if st == models.RechargeOrderPending || st == models.RechargeOrderPaid || st == models.RechargeOrderCancelled {
		countQ = countQ.Where("o.status = ?", st)
	}
	countQ.Count(&total)

	var list []AdminRechargeOrderRow
	q.Order("o.id DESC").Offset(offset).Limit(pageSize).Scan(&list)

	utils.Success(c, gin.H{
		"list":           list,
		"total":          total,
		"page":           page,
		"page_size":      pageSize,
		"currency_name":  wallet.CurrencyName,
		"coins_per_yuan": wallet.CoinsPerYuan,
	})
}
