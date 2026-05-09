package models

import (
	"time"

	"gorm.io/gorm"
)

// AppReleasePackage 客户端安装包 / 版本更新（软删）
type AppReleasePackage struct {
	ID            uint           `json:"id" gorm:"primaryKey"`
	Name          string         `json:"name" gorm:"size:200;not null"`
	Platform      string         `json:"platform" gorm:"size:20;not null;index"` // android | ios
	Version       string         `json:"version" gorm:"size:32;not null"`
	Enabled        bool           `json:"enabled" gorm:"default:false;index"`
	ForceUpdate    bool           `json:"force_update" gorm:"default:false"`
	ReleaseNotes   string         `json:"release_notes" gorm:"type:text"`
	ApkPath       string         `json:"apk_path" gorm:"size:512"`
	IpaPath       string         `json:"ipa_path" gorm:"size:512"`
	ManifestPath  string         `json:"manifest_path" gorm:"size:512"`
	CreatedAt     time.Time      `json:"created_at"`
	UpdatedAt     time.Time      `json:"updated_at"`
	DeletedAt     gorm.DeletedAt `json:"-" gorm:"index"`
}

func (AppReleasePackage) TableName() string {
	return "app_release_packages"
}
