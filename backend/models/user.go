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
	ID           uint      `json:"id" gorm:"primaryKey"`
	Username     string    `json:"username" gorm:"size:50;uniqueIndex;not null"`
	Nickname     string    `json:"nickname" gorm:"size:50"`
	Avatar       string    `json:"avatar" gorm:"size:500"`
	Phone        string    `json:"phone" gorm:"size:20;index"`
	PasswordHash string    `json:"-" gorm:"size:200;not null"`
	Coins        int       `json:"coins" gorm:"default:0"`
	VipLevel     int       `json:"vip_level" gorm:"default:0"`
	Status       int       `json:"status" gorm:"default:1"`
	CreatedAt    time.Time `json:"created_at"`
}
