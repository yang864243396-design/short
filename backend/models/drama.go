package models

import "time"

type Drama struct {
	ID            uint      `json:"id" gorm:"primaryKey"`
	Title         string    `json:"title" gorm:"size:200;not null"`
	CoverURL      string    `json:"cover_url" gorm:"size:500"`
	Description   string    `json:"description" gorm:"type:text"`
	Category      string    `json:"category" gorm:"size:50"`
	Tags          string    `json:"tags" gorm:"size:200"`
	TotalEpisodes int       `json:"total_episodes" gorm:"default:0"`
	Rating        float32   `json:"rating" gorm:"default:0"`
	Heat          int64     `json:"heat" gorm:"default:0"`
	Status        string    `json:"status" gorm:"size:20;default:'ongoing'"`
	Enabled       bool      `json:"enabled" gorm:"default:true"`
	CreatedAt     time.Time `json:"created_at"`
	UpdatedAt     time.Time `json:"updated_at"`
}

type Category struct {
	ID   uint   `json:"id" gorm:"primaryKey"`
	Name string `json:"name" gorm:"size:50;uniqueIndex"`
	Sort int    `json:"sort" gorm:"default:0"`
}
