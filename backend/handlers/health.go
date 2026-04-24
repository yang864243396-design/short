package handlers

import (
	"github.com/gin-gonic/gin"
	"short-drama-backend/config"
	"short-drama-backend/database"
	"short-drama-backend/utils"
)

// GetHealth 返回 DB/Redis 是否可用，并附带当前读取到的 Redis 连接信息（不含密码），便于对照 .env 与服务器实际 Redis。
func GetHealth(c *gin.Context) {
	cfg := config.Load()

	dbOK := false
	if database.DB != nil {
		if sqlDB, err := database.DB.DB(); err == nil {
			dbOK = sqlDB.Ping() == nil
		}
	}

	redisOK := false
	redisPingErr := ""
	if utils.Rdb != nil {
		if err := utils.Rdb.Ping(utils.Ctx).Err(); err != nil {
			redisPingErr = err.Error()
		} else {
			redisOK = true
		}
	}

	out := gin.H{
		"status": "ok",
		"db":     dbOK,
		"redis":  redisOK,
		"redis_config": gin.H{
			"addr":         cfg.Redis.Addr,
			"db":           cfg.Redis.DB,
			"password_set": cfg.Redis.Password != "",
		},
	}
	if redisPingErr != "" {
		out["redis_ping_error"] = redisPingErr
		out["redis_hint"] = utils.RedisConnectHintFromString(redisPingErr)
	}
	utils.Success(c, out)
}
