package models

import "time"

const AllPermissions = "dashboard,dramas,categories,users,admins,comments,banners,ads,roles"

type AdminRole struct {
	ID          uint      `json:"id" gorm:"primaryKey"`
	Name        string    `json:"name" gorm:"size:50;uniqueIndex;not null"`
	Description string    `json:"description" gorm:"size:200"`
	Permissions string    `json:"permissions" gorm:"type:text"`
	CreatedAt   time.Time `json:"created_at"`
}
