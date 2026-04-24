package models

import "time"

type Episode struct {
	ID            uint      `json:"id" gorm:"primaryKey"`
	DramaID       uint      `json:"drama_id" gorm:"index;not null"`
	EpisodeNumber int       `json:"episode_number" gorm:"not null"`
	Title         string    `json:"title" gorm:"size:200"`
	VideoURL      string    `json:"video_url" gorm:"size:500"`
	VideoPath     string    `json:"video_path" gorm:"size:500"`
	FileSize      int64     `json:"file_size" gorm:"default:0"`
	IsFree        bool      `json:"is_free" gorm:"default:false"`
	UnlockCoins   int       `json:"unlock_coins" gorm:"default:0"` // 非免费集：用户支付该金币数永久解锁本集（免广告）
	LikeCount     int64     `json:"like_count" gorm:"default:0"`
	CommentCount  int64     `json:"comment_count" gorm:"default:0"`
	ViewCount     int64     `json:"view_count" gorm:"default:0"`
	CreatedAt     time.Time `json:"created_at"`
}
