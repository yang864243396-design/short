package models

import "time"

// 钱包流水类型（与业务层常量一致）
const (
	WalletTxRecharge = "recharge" // 充值（增加金币）
	WalletTxConsume  = "consume"  // 消费（扣减金币）
)

// 流水业务引用类型（App 端仅展示以下四类）
const (
	WalletRefRechargeOrder  = "recharge_order"  // 用户充值金币
	WalletRefAdminRecharge  = "admin_recharge"  // 后台赠送
	WalletRefAdSkipPurchase = "ad_skip_purchase" // 购买广告跳过卡
	WalletRefEpisodeUnlock  = "episode_unlock"   // 分集金币永久解锁
	WalletRefAdminDeduct    = "admin_deduct"    // 后台扣除
)

// WalletTransaction 金币流水；余额以 app_users.coins 为准，本表仅追加记录。
type WalletTransaction struct {
	ID           uint      `json:"id" gorm:"primaryKey"`
	UserID       uint      `json:"user_id" gorm:"index:idx_wallet_tx_user_created,priority:1;not null"`
	Type         string    `json:"type" gorm:"size:20;index;not null"` // recharge / consume
	Amount       int       `json:"amount" gorm:"not null"`             // 变动金币数，恒为正
	BalanceAfter int       `json:"balance_after" gorm:"not null"`      // 变动后余额
	Title        string    `json:"title" gorm:"size:100"`
	Remark       string    `json:"remark" gorm:"size:500"`
	RefType      string    `json:"ref_type" gorm:"size:50"`
	RefID        uint      `json:"ref_id" gorm:"default:0"`
	MchOrderNo   string    `json:"mch_order_no,omitempty" gorm:"size:32;index"` // 充值订单对应商户单号（新支付；旧流水为空）
	OperatorID   uint      `json:"operator_id" gorm:"default:0"` // 后台人工充值时的管理员 id
	CreatedAt    time.Time `json:"created_at" gorm:"index:idx_wallet_tx_user_created,priority:2"`
}
