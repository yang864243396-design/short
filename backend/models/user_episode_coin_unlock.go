package models

import "time"

// UserEpisodeCoinUnlock 用户金币永久解锁分集（免广告观看）
type UserEpisodeCoinUnlock struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	UserID    uint      `json:"user_id" gorm:"not null;uniqueIndex:uq_user_episode_coin"`
	EpisodeID uint      `json:"episode_id" gorm:"not null;uniqueIndex:uq_user_episode_coin"`
	CreatedAt time.Time `json:"created_at"`
}
