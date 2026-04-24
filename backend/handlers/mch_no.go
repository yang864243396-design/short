package handlers

import (
	"errors"
	"fmt"
	"math"
	"strings"
	"time"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"short-drama-backend/models"
)

func pow10(n int) int {
	x := 1
	for i := 0; i < n; i++ {
		x *= 10
	}
	return x
}

// formatMchSeq 序号段：最少 4 位，溢出后增加宽度（§5）
func formatMchSeq(n int) string {
	if n < 1 {
		n = 1
	}
	w := 4
	for n >= pow10(w) {
		w++
	}
	if w < 4 {
		w = 4
	}
	return fmt.Sprintf("%0*d", w, n)
}

// padProductID4 4 位十进制 productId
func padProductID4(s string) string {
	s = strings.TrimSpace(s)
	if len(s) > 4 {
		s = s[len(s)-4:]
	}
	for len(s) < 4 {
		s = "0" + s
	}
	return s
}

// allocMchOrderNo 在**同一事务**内生成 20+序号，保证并发唯一
func allocMchOrderNo(tx *gorm.DB, userID uint, productID4 string) (string, error) {
	if userID > 99999999 {
		return "", errors.New("用户编号超出 8 位可表示范围")
	}
	now := time.Now().In(time.Local)
	y, m, d := now.Date()
	dayStart := time.Date(y, m, d, 0, 0, 0, 0, now.Location())
	dayKey := dayStart.Format("20060102")
	p := padProductID4(productID4)
	if len(p) != 4 {
		return "", errors.New("支付产品ID无效")
	}
	prefix := dayKey + p + fmt.Sprintf("%08d", userID)
	if len(prefix) != 20 {
		return "", errors.New("mch 前缀异常")
	}

	for attempt := 0; attempt < 4; attempt++ {
		var row models.RechargeMchSeq
		err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("user_id = ? AND day_key = ?", userID, dayKey).First(&row).Error
		if errors.Is(err, gorm.ErrRecordNotFound) {
			row = models.RechargeMchSeq{UserID: userID, DayKey: dayKey, LastSeq: 1}
			if e := tx.Create(&row).Error; e != nil {
				continue
			}
			full := prefix + formatMchSeq(1)
			if len(full) > 30 {
				return "", errors.New("订单号超过 30 位")
			}
			return full, nil
		}
		if err != nil {
			return "", err
		}
		row.LastSeq++
		if e := tx.Save(&row).Error; e != nil {
			return "", e
		}
		full := prefix + formatMchSeq(row.LastSeq)
		if len(full) > 30 {
			return "", errors.New("订单号超过 30 位")
		}
		return full, nil
	}
	return "", errors.New("发号失败，请重试")
}

// todayPendingRechargeCount 当日在途未支付（pending）笔数
func todayPendingRechargeCount(tx *gorm.DB, userID uint) (int64, error) {
	now := time.Now().In(time.Local)
	y, m, d := now.Date()
	start := time.Date(y, m, d, 0, 0, 0, 0, now.Location())
	end := start.Add(24 * time.Hour)
	var n int64
	err := tx.Model(&models.RechargeOrder{}).Where("user_id = ? AND status = ? AND created_at >= ? AND created_at < ?",
		userID, models.RechargeOrderPending, start, end).Count(&n).Error
	return n, err
}

// maybeLazyCancelRecharge 懒关单 10 分钟
func maybeLazyCancelRecharge(tx *gorm.DB, o *models.RechargeOrder) {
	if o == nil {
		return
	}
	if o.Status != models.RechargeOrderPending {
		return
	}
	if time.Since(o.CreatedAt) < 10*time.Minute {
		return
	}
	now := time.Now()
	o.Status = models.RechargeOrderCancelled
	o.CancelledAt = &now
	_ = tx.Model(o).Updates(map[string]any{
		"status":         models.RechargeOrderCancelled,
		"cancelled_at":   now,
		"updated_at":     now,
	})
}

// yuanToFen 套餐价转分，与 §2 无歧义
func yuanToFen(y float64) int {
	if y <= 0 {
		return 0
	}
	return int(math.Round(y * 100))
}

func subjectSnapFromPackage(name string) string {
	r := []rune(name)
	if len(r) > 64 {
		return string(r[:64])
	}
	return name
}
