package models

import "time"

type DramaStats struct {
	ID             uint    `json:"id" gorm:"primaryKey"`
	DramaID        uint    `json:"drama_id" gorm:"uniqueIndex;not null"`
	TotalViews     int64   `json:"total_views" gorm:"default:0"`
	TotalLikes     int64   `json:"total_likes" gorm:"default:0"`
	TotalCollects  int64   `json:"total_collects" gorm:"default:0"`
	TotalComments  int64   `json:"total_comments" gorm:"default:0"`
	AvgRating      float32 `json:"avg_rating" gorm:"default:0"`
	RatingCount    int64   `json:"rating_count" gorm:"default:0"`
	Views7Day      int64   `json:"views_7day" gorm:"column:views_7day;default:0"`
	ViewsPrev7Day  int64   `json:"views_prev_7day" gorm:"column:views_prev_7day;default:0"`
	RisingScore    float64 `json:"rising_score" gorm:"column:rising_score;default:0"`
	UpdatedAt      time.Time `json:"updated_at"`
}

type DailySnapshot struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	DramaID   uint      `json:"drama_id" gorm:"uniqueIndex:idx_drama_date;not null"`
	Date      string    `json:"date" gorm:"size:10;uniqueIndex:idx_drama_date;not null"`
	Views     int64     `json:"views" gorm:"default:0"`
	Likes     int64     `json:"likes" gorm:"default:0"`
	Collects  int64     `json:"collects" gorm:"default:0"`
	CreatedAt time.Time `json:"created_at"`
}

type UserRating struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	UserID    uint      `json:"user_id" gorm:"index;not null"`
	DramaID   uint      `json:"drama_id" gorm:"index;not null"`
	Score     float32   `json:"score" gorm:"not null"`
	CreatedAt time.Time `json:"created_at"`
}
