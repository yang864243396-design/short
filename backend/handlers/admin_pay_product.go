package handlers

import (
	"github.com/gin-gonic/gin"
	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

// AdminListPayProductConfigs 管理端：支付产品列表（含软删展示需用 Unscoped 时另做）
func AdminListPayProductConfigs(c *gin.Context) {
	var list []models.PayProductConfig
	database.DB.Order("sort ASC, id ASC").Find(&list)
	utils.Success(c, gin.H{"list": list})
}

func AdminCreatePayProductConfig(c *gin.Context) {
	var row models.PayProductConfig
	if err := c.ShouldBindJSON(&row); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	row.ProductID = padProductID4(row.ProductID)
	if len(row.ProductID) != 4 {
		utils.BadRequest(c, "product_id 需为 4 位十进制")
		return
	}
	if row.Name == "" {
		utils.BadRequest(c, "请填写名称")
		return
	}
	var n int64
	database.DB.Model(&models.PayProductConfig{}).Where("product_id = ?", row.ProductID).Count(&n)
	if n > 0 {
		utils.BadRequest(c, "已存在该 product_id")
		return
	}
	database.DB.Create(&row)
	utils.Success(c, row)
}

func AdminUpdatePayProductConfig(c *gin.Context) {
	id := c.Param("id")
	var row models.PayProductConfig
	if err := database.DB.First(&row, id).Error; err != nil {
		utils.BadRequest(c, "记录不存在")
		return
	}
	var body models.PayProductConfig
	if err := c.ShouldBindJSON(&body); err != nil {
		utils.BadRequest(c, "参数错误")
		return
	}
	p4 := padProductID4(body.ProductID)
	if len(p4) != 4 {
		utils.BadRequest(c, "product_id 需为 4 位十进制")
		return
	}
	if body.Name == "" {
		utils.BadRequest(c, "请填写名称")
		return
	}
	if p4 != row.ProductID {
		var n int64
		database.DB.Model(&models.PayProductConfig{}).Where("product_id = ? AND id != ?", p4, row.ID).Count(&n)
		if n > 0 {
			utils.BadRequest(c, "已存在该 product_id")
			return
		}
	}
	row.ProductID = p4
	row.Name = body.Name
	row.Enabled = body.Enabled
	row.Sort = body.Sort
	database.DB.Save(&row)
	utils.Success(c, row)
}

// AdminDeletePayProductConfig 软删（禁止硬删物理行）
func AdminDeletePayProductConfig(c *gin.Context) {
	id := c.Param("id")
	if err := database.DB.Delete(&models.PayProductConfig{}, id).Error; err != nil {
		utils.ServerError(c, "删除失败")
		return
	}
	utils.Success(c, nil)
}
