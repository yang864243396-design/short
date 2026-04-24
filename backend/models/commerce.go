package models

import "time"

// RechargePackage 用户端充值档位（人民币标价；到账基础金币 + 赠送金币）
type RechargePackage struct {
	ID         uint      `json:"id" gorm:"primaryKey"`
	Name       string    `json:"name" gorm:"size:100;not null"`
	Coins      int       `json:"coins" gorm:"not null"`
	BonusCoins int       `json:"bonus_coins" gorm:"default:0;not null"` // 赠送金币（额外到账，默认 0）
	PriceYuan  float64   `json:"price_yuan" gorm:"type:decimal(10,2);not null"`
	Enabled    bool      `json:"enabled" gorm:"default:true"`
	Sort       int       `json:"sort" gorm:"default:0"`
	CreatedAt  time.Time `json:"created_at"`
	UpdatedAt  time.Time `json:"updated_at"`
}

const (
	RechargeOrderPending   = "pending"
	RechargeOrderPaid      = "paid"
	RechargeOrderCancelled = "cancelled"
)

// RechargeOrder 用户充值订单（聚合：先 pending，notify/查单 幂等入账；无三方时可为空 mch_order_no）
type RechargeOrder struct {
	ID            uint       `json:"id" gorm:"primaryKey"`
	UserID        uint       `json:"user_id" gorm:"index;not null"`
	PackageID     uint       `json:"package_id" gorm:"not null"`
	Coins         int        `json:"coins" gorm:"not null"`
	PriceYuan     float64    `json:"price_yuan" gorm:"type:decimal(10,2);not null"`
	Status        string     `json:"status" gorm:"size:20;index;not null"`
	Remark        string     `json:"remark" gorm:"size:200"`
	PaidAt        *time.Time `json:"paid_at"`
	CreatedAt     time.Time  `json:"created_at"`
	UpdatedAt     time.Time  `json:"updated_at"`
	MchOrderNo    *string    `json:"mch_order_no" gorm:"size:32;uniqueIndex"` // 有聚合单号时唯一
	PayOrderID    string     `json:"pay_order_id" gorm:"size:40"`              // 平台支付单号
	ProductID     string     `json:"product_id" gorm:"size:4"`                // 所选 4 位产品
	AmountFen     int        `json:"amount_fen" gorm:"not null;default:0"`     // 上送聚合金额（分）
	SubjectSnap   string     `json:"subject_snapshot" gorm:"size:128"`         // 下单时 subject/body 快照
	CancelledAt   *time.Time `json:"cancelled_at"`                             // 懒关单写库时间（与 §6 报表一致）
}

// AdSkipPackageTypeTime 时间包（时长+次数）；AdSkipPackageTypeBooster 加油包（仅加次数，须时间包有效内购买）
const (
	AdSkipPackageTypeTime    = "time"
	AdSkipPackageTypeBooster = "booster"
)

// AdSkipConfig 广告跳过卡档位（多行；用户端仅展示 enabled 且价格/时长有效的记录）
type AdSkipConfig struct {
	ID            uint      `json:"id" gorm:"primaryKey"`
	Name          string    `json:"name" gorm:"size:100;not null;default:''"`
	PackageType   string    `json:"package_type" gorm:"size:16;default:time;index"` // time | booster
	DurationHours int       `json:"duration_hours" gorm:"not null"`                   // 加油包为 0
	SkipCount     int       `json:"skip_count" gorm:"not null;default:100"`           // 时间包：赠送次数；加油包：单次加几次
	PriceCoins    int       `json:"price_coins" gorm:"not null"`
	Enabled       bool      `json:"enabled" gorm:"default:true"`
	Sort          int       `json:"sort" gorm:"default:0"`
	CreatedAt     time.Time `json:"created_at"`
	UpdatedAt     time.Time `json:"updated_at"`
}
