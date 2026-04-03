package models

import "time"

type AdVideo struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Title     string    `json:"title" gorm:"size:200;not null"`
	VideoPath string    `json:"video_path" gorm:"size:500"`
	Duration  int       `json:"duration" gorm:"default:15"`
	Enabled   bool      `json:"enabled" gorm:"default:true"`
	Weight    int       `json:"weight" gorm:"default:1"`
	CreatedAt time.Time `json:"created_at"`
}
