package models

import "time"

type UserLike struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	UserID    uint      `json:"user_id" gorm:"index;not null"`
	EpisodeID uint      `json:"episode_id" gorm:"index;not null"`
	CreatedAt time.Time `json:"created_at"`
}

type UserCollect struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	UserID    uint      `json:"user_id" gorm:"index;not null"`
	DramaID   uint      `json:"drama_id" gorm:"index;not null"`
	CreatedAt time.Time `json:"created_at"`
}

type UserHistory struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	UserID    uint      `json:"user_id" gorm:"index;not null"`
	DramaID   uint      `json:"drama_id"`
	EpisodeID uint      `json:"episode_id"`
	Progress  int       `json:"progress"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type CommentLike struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	UserID    uint      `json:"user_id" gorm:"index"`
	CommentID uint      `json:"comment_id" gorm:"index"`
	CreatedAt time.Time `json:"created_at"`
}

type CheckIn struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	UserID    uint      `json:"user_id" gorm:"index;not null"`
	Coins     int       `json:"coins"`
	CreatedAt time.Time `json:"created_at"`
}

type WelfareTask struct {
	ID          uint   `json:"id" gorm:"primaryKey"`
	Title       string `json:"title" gorm:"size:100"`
	Icon        string `json:"icon" gorm:"size:20"`
	Reward      string `json:"reward" gorm:"size:50"`
	RewardCoins int    `json:"reward_coins"`
	TaskType    string `json:"task_type" gorm:"size:50"`
}
