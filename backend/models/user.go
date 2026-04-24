package models

import "time"

type Admin struct {
	ID           uint      `json:"id" gorm:"primaryKey"`
	Username     string    `json:"username" gorm:"size:50;uniqueIndex;not null"`
	Nickname     string    `json:"nickname" gorm:"size:50"`
	Avatar       string    `json:"avatar" gorm:"size:500"`
	PasswordHash string    `json:"-" gorm:"size:200;not null"`
	RoleID       uint      `json:"role_id" gorm:"default:0"`
	Status       int       `json:"status" gorm:"default:1"`
	CreatedAt    time.Time `json:"created_at"`
}

type AppUser struct {
	ID               uint       `json:"id" gorm:"primaryKey"`
	Username         string     `json:"username" gorm:"size:50;uniqueIndex;not null"` // 登录名；软删除后改为占位串以释放邮箱给新账号
	RegisteredEmail  string     `json:"registered_email" gorm:"size:191"`             // 注册邮箱（软删除后仍保留便于后台核对）
	Nickname         string     `json:"nickname" gorm:"size:50"`
	Avatar           string     `json:"avatar" gorm:"size:500"`
	Phone            string     `json:"phone" gorm:"size:20;index"`
	PasswordHash     string     `json:"-" gorm:"size:200;not null"`
	Coins             int        `json:"coins" gorm:"default:0"`
	AdSkipExpiresAt   *time.Time `json:"ad_skip_expires_at"`
	AdSkipRemaining   int        `json:"ad_skip_remaining" gorm:"default:0"` // 有效期内免广告次数；到期清零
	Status            int        `json:"status" gorm:"default:1"`
	DeletedAt        *time.Time `json:"deleted_at" gorm:"index"` // 非空表示已软删除，不可登录
	CreatedAt        time.Time  `json:"created_at"`
}
