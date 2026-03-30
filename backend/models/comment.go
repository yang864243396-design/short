package models

import "time"

type Comment struct {
	ID         uint      `json:"id" gorm:"primaryKey"`
	EpisodeID  uint      `json:"episode_id" gorm:"index;not null"`
	UserID     uint      `json:"user_id" gorm:"index;not null"`
	Nickname   string    `json:"nickname" gorm:"-"`
	Avatar     string    `json:"avatar" gorm:"-"`
	Content    string    `json:"content" gorm:"type:text;not null"`
	LikesCount int       `json:"likes_count" gorm:"default:0"`
	TimeAgo    string    `json:"time_ago" gorm:"-"`
	Liked      bool      `json:"liked" gorm:"-"`
	CreatedAt  time.Time `json:"created_at"`
}
