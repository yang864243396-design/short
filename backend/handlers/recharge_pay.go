package handlers

import (
	"fmt"
	"time"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"short-drama-backend/models"
	"short-drama-backend/wallet"
)

// tryCreditRechargeFromGateway notify/查单 共用：验额+幂等入账；gatewayStatus 2/3 为成功可入账
func tryCreditRechargeFromGateway(tx *gorm.DB, mchOrderNo string, payOrderID string, amountFen, gatewayStatus int) (balanceAfter int, ok bool, err error) {
	if gatewayStatus != 2 && gatewayStatus != 3 {
		return 0, true, nil
	}
	var o models.RechargeOrder
	if err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).Where("mch_order_no = ?", mchOrderNo).First(&o).Error; err != nil {
		return 0, false, err
	}
	if amountFen != o.AmountFen {
		return 0, false, fmt.Errorf("amount mismatch")
	}
	if o.Status == models.RechargeOrderPaid {
		var u models.AppUser
		if err := tx.Select("coins").Where("id = ?", o.UserID).First(&u).Error; err != nil {
			return 0, false, err
		}
		return u.Coins, true, nil
	}
	if o.Status != models.RechargeOrderPending && o.Status != models.RechargeOrderCancelled {
		return 0, false, fmt.Errorf("order not payable")
	}

	mch := mchOrderNo
	title := "充值金币"
	remark := fmt.Sprintf("订单·%s·套餐#%d", mch, o.PackageID)
	bal, werr := wallet.Apply(tx, o.UserID, models.WalletTxRecharge, o.Coins, title, remark, models.WalletRefRechargeOrder, o.ID, 0, mch)
	if werr != nil {
		return 0, false, werr
	}
	now := time.Now()
	o.Status = models.RechargeOrderPaid
	o.PaidAt = &now
	if payOrderID != "" {
		o.PayOrderID = payOrderID
	}
	if err := tx.Save(&o).Error; err != nil {
		return 0, false, err
	}
	return bal, true, nil
}
