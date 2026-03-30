package utils

import (
	"context"
	"encoding/json"
	"time"

	"github.com/go-redis/redis/v8"
	"short-drama-backend/config"
)

var Rdb *redis.Client
var Ctx = context.Background()

func InitRedis(cfg config.RedisConfig) {
	Rdb = redis.NewClient(&redis.Options{
		Addr:     cfg.Addr,
		Password: cfg.Password,
		DB:       cfg.DB,
	})
}

func CacheSet(key string, value interface{}, ttl time.Duration) error {
	data, err := json.Marshal(value)
	if err != nil {
		return err
	}
	return Rdb.Set(Ctx, key, data, ttl).Err()
}

func CacheGet(key string, dest interface{}) error {
	data, err := Rdb.Get(Ctx, key).Bytes()
	if err != nil {
		return err
	}
	return json.Unmarshal(data, dest)
}

func CacheDelete(key string) error {
	return Rdb.Del(Ctx, key).Err()
}
