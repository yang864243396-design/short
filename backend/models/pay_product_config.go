package models

import (
	"time"

	"gorm.io/gorm"
)

// PayProductConfig 聚合支付产品（4 位 productId，管理端配置；禁止硬删，用软删）
type PayProductConfig struct {
	ID        uint           `json:"id" gorm:"primaryKey"`
	ProductID string         `json:"product_id" gorm:"size:4;not null;index"` // 4 位十进制展示，如 "8010"
	Name      string         `json:"name" gorm:"size:100;not null"`
	Enabled   bool           `json:"enabled" gorm:"default:true"`
	Sort      int            `json:"sort" gorm:"default:0"`
	CreatedAt time.Time      `json:"created_at"`
	UpdatedAt time.Time      `json:"updated_at"`
	DeletedAt gorm.DeletedAt `json:"-" gorm:"index"`
}
