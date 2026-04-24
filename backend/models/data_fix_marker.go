package models

// DataFixMarker 记录已执行的一次性数据修复，避免每次进程启动重复跑。
type DataFixMarker struct {
	ID     uint   `json:"id" gorm:"primaryKey"`
	Marker string `json:"marker" gorm:"size:80;uniqueIndex;not null"`
}
