package wallet

import (
	"errors"
	"math"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"short-drama-backend/models"
)

// 货币规则（写死）
const (
	CurrencyName = "金币"
	CoinsPerYuan = 100 // 1 元人民币 = 100 金币
)

var (
	ErrInvalidAmount   = errors.New("金币数量无效")
	ErrInsufficient    = errors.New("金币余额不足")
	ErrUnknownTxType   = errors.New("未知的流水类型")
	ErrAccountDeleted  = errors.New("账号已注销")
)

// YuanToCoins 将人民币金额转为金币（四舍五入到整数金币）。
func YuanToCoins(yuan float64) int {
	if yuan <= 0 {
		return 0
	}
	return int(math.Round(yuan * float64(CoinsPerYuan)))
}

// CoinsToYuan 将金币转为人民币浮点表示（仅展示用）。
func CoinsToYuan(coins int) float64 {
	return float64(coins) / float64(CoinsPerYuan)
}

// Apply 在事务内锁定用户、更新 coins、写入流水。amount 必须为正；consume 时余额不足返回 ErrInsufficient。
// mchOrderNo 仅部分业务（用户充值对聚合单号）写入流水，其余传空串。
func Apply(tx *gorm.DB, userID uint, typ string, amount int, title, remark, refType string, refID uint, operatorID uint, mchOrderNo string) (balanceAfter int, err error) {
	if amount <= 0 {
		return 0, ErrInvalidAmount
	}

	var u models.AppUser
	if err = tx.Clauses(clause.Locking{Strength: "UPDATE"}).First(&u, userID).Error; err != nil {
		return 0, err
	}
	if u.DeletedAt != nil {
		return 0, ErrAccountDeleted
	}

	var newBal int
	switch typ {
	case models.WalletTxRecharge:
		newBal = u.Coins + amount
	case models.WalletTxConsume:
		if u.Coins < amount {
			return 0, ErrInsufficient
		}
		newBal = u.Coins - amount
	default:
		return 0, ErrUnknownTxType
	}

	if err = tx.Model(&models.AppUser{}).Where("id = ?", userID).Update("coins", newBal).Error; err != nil {
		return 0, err
	}

	row := models.WalletTransaction{
		UserID:       userID,
		Type:         typ,
		Amount:       amount,
		BalanceAfter: newBal,
		Title:        title,
		Remark:       remark,
		RefType:      refType,
		RefID:        refID,
		OperatorID:   operatorID,
		MchOrderNo:   mchOrderNo,
	}
	if err = tx.Create(&row).Error; err != nil {
		return 0, err
	}
	return newBal, nil
}
