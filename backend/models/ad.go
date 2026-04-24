package models

import "time"

type AdVideo struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Title     string    `json:"title" gorm:"size:200;not null"`
	MediaType string    `json:"media_type" gorm:"size:20;default:'video'"` // video | image
	VideoPath string    `json:"video_path" gorm:"size:500"`
	ImageURL  string    `json:"image_url" gorm:"size:500"` // 如 /uploads/images/xxx.jpg，静态资源直链
	Duration  int       `json:"duration" gorm:"default:15"`
	Enabled   bool      `json:"enabled" gorm:"default:true"`
	Weight    int       `json:"weight" gorm:"default:1"`
	CreatedAt time.Time `json:"created_at"`
}
