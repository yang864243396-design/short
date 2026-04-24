package models

import "time"

// RechargeMchSeq 按用户+自然日递增的商户单号序列表（与 §5 序号段配合）
type RechargeMchSeq struct {
	ID       uint      `json:"id" gorm:"primaryKey"`
	UserID   uint      `json:"user_id" gorm:"uniqueIndex:idx_user_day;not null"`
	DayKey   string    `json:"day_key" gorm:"size:8;uniqueIndex:idx_user_day;not null"` // YYYYMMDD
	LastSeq   int       `json:"last_seq" gorm:"not null;default:0"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}
