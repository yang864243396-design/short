package models

import "time"

type Comment struct {
	ID              uint      `json:"id" gorm:"primaryKey"`
	EpisodeID       uint      `json:"episode_id" gorm:"index:idx_comments_ep_parent_created,priority:1;not null"`
	UserID          uint      `json:"user_id" gorm:"index;not null"`
	ParentID        uint      `json:"parent_id" gorm:"index:idx_comments_ep_parent_created,priority:2;default:0"`
	ReplyToUserID   uint      `json:"reply_to_user_id" gorm:"default:0"`
	Nickname        string    `json:"nickname" gorm:"-"`
	Avatar          string    `json:"avatar" gorm:"-"`
	ReplyToNickname string    `json:"reply_to_nickname" gorm:"-"`
	Content         string    `json:"content" gorm:"type:text;not null"`
	LikesCount      int       `json:"likes_count" gorm:"default:0"`
	TimeAgo         string    `json:"time_ago" gorm:"-"`
	Liked           bool      `json:"liked" gorm:"-"`
	CreatedAt       time.Time `json:"created_at" gorm:"index:idx_comments_ep_parent_created,priority:3"`
	Replies         []Comment `json:"replies,omitempty" gorm:"-"`
	ReplyCount      int       `json:"reply_count,omitempty" gorm:"-"`
	HasMoreReplies  bool      `json:"has_more_replies,omitempty" gorm:"-"`
}
