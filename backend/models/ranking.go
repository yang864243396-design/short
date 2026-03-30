package models

import "time"

type HotSearch struct {
	ID      uint   `json:"id" gorm:"primaryKey"`
	Keyword string `json:"keyword" gorm:"size:100;not null"`
	Heat    int64  `json:"heat" gorm:"default:0"`
	Badge   string `json:"badge" gorm:"size:10"`
	Rank    int    `json:"rank" gorm:"default:0"`
}

type SearchHistory struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	UserID    uint      `json:"user_id" gorm:"index;not null"`
	Keyword   string    `json:"keyword" gorm:"size:100;not null"`
	CreatedAt time.Time `json:"created_at"`
}

type FeedRecommend struct {
	ID        uint `json:"id" gorm:"primaryKey"`
	EpisodeID uint `json:"episode_id" gorm:"index;not null"`
	Weight    int  `json:"weight" gorm:"default:0"`
	Status    int  `json:"status" gorm:"default:1"`
}
