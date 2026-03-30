package models

import "time"

type Banner struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Title     string    `json:"title" gorm:"size:200;not null"`
	ImageURL  string    `json:"image_url" gorm:"size:500;not null"`
	LinkType  string    `json:"link_type" gorm:"size:20;not null;default:'url'"` // "url" or "drama"
	LinkURL   string    `json:"link_url" gorm:"size:500"`                        // third-party URL when link_type=url
	DramaID   uint      `json:"drama_id" gorm:"default:0"`                      // drama ID when link_type=drama
	Sort      int       `json:"sort" gorm:"default:0"`
	Status    int       `json:"status" gorm:"default:1"` // 1=active, 0=inactive
	StartTime *time.Time `json:"start_time"`
	EndTime   *time.Time `json:"end_time"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}
