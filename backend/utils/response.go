package utils

import (
	"net/http"
	"github.com/gin-gonic/gin"
)

type Response struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data"`
}

func Success(c *gin.Context, data interface{}) {
	c.JSON(http.StatusOK, Response{Code: 200, Message: "success", Data: data})
}

func Error(c *gin.Context, code int, msg string) {
	c.JSON(code, Response{Code: code, Message: msg, Data: nil})
}

func BadRequest(c *gin.Context, msg string) {
	Error(c, http.StatusBadRequest, msg)
}

func Unauthorized(c *gin.Context) {
	Error(c, http.StatusUnauthorized, "未授权，请先登录")
}

func ServerError(c *gin.Context, msg string) {
	Error(c, http.StatusInternalServerError, msg)
}
