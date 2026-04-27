package models

import "time"

// DramaRankingInfo 榜单信息（由详情等接口根据 Redis 榜单缓存动态填充，不入库）
type DramaRankingInfo struct {
	List string `json:"list"` // hot | rising | rating
	Rank int    `json:"rank"`
}

type Drama struct {
	ID            uint    `json:"id" gorm:"primaryKey"`
	Title         string  `json:"title" gorm:"size:200;not null"`
	RecommendSort *int    `json:"recommend_sort" gorm:"index"`
	CoverURL      string  `json:"cover_url" gorm:"size:500"`
	Description   string  `json:"description" gorm:"type:text"`
	Category      string  `json:"category" gorm:"size:200"`
	TotalEpisodes int     `json:"total_episodes" gorm:"default:0"`
	Rating        float32 `json:"rating" gorm:"default:0"`
	Heat          int64   `json:"heat" gorm:"default:0"`
	Status        string  `json:"status" gorm:"size:20;default:'ongoing'"`
	// Enabled 上架：true=正常（对用户端展示、进榜单、可播放）；false=下架（全端隐藏、不参与排行）
	Enabled   bool      `json:"enabled" gorm:"default:true"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`

	Ranking *DramaRankingInfo `json:"ranking,omitempty" gorm:"-"`
}

type Category struct {
	ID   uint   `json:"id" gorm:"primaryKey"`
	Name string `json:"name" gorm:"size:50;uniqueIndex"`
	Sort int    `json:"sort" gorm:"default:0"`
}
