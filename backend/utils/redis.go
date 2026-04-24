package utils

import (
	"context"
	"encoding/json"
	"log"
	"strings"
	"time"

	"github.com/go-redis/redis/v8"
	"short-drama-backend/config"
)

var Rdb *redis.Client
var Ctx = context.Background()

// RedisConnectHint 根据 go-redis 返回的错误给出中文说明（程序无法替你判断「Redis 服务是否已装」，只能根据连接错误推断）。
func RedisConnectHint(err error) string {
	if err == nil {
		return ""
	}
	return redisConnectHintBody(err.Error())
}

// RedisConnectHintFromString 与 RedisConnectHint 相同逻辑，供健康检查等仅有错误字符串的场景使用。
func RedisConnectHintFromString(errStr string) string {
	return redisConnectHintBody(errStr)
}

func redisConnectHintBody(errStr string) string {
	s := strings.ToLower(errStr)
	switch {
	case strings.Contains(s, "connection refused"):
		return "本机该端口无进程监听：Redis 很可能未启动，或 REDIS_ADDR/端口错误。请在服务器执行: ss -tlnp | grep 6379；宝塔「软件商店」确认 Redis 已安装并运行；再执行 redis-cli ping"
	case strings.Contains(s, "without any password configured") || strings.Contains(s, "auth <password> called without"):
		return "Redis 未配置密码，但 .env 里填写了 REDIS_PASSWORD，请删除 REDIS_PASSWORD 或留空；或在宝塔 Redis 设置里为该实例设置密码并保持一致"
	case strings.Contains(s, "noauth") || strings.Contains(s, "wrongpass") || strings.Contains(s, "invalid password"):
		return "认证失败：.env 中 REDIS_PASSWORD 与宝塔里设置的 Redis 密码不一致，或 Redis 要求密码但你未填写"
	case strings.Contains(s, "timeout") || strings.Contains(s, "i/o timeout"):
		return "连接超时：REDIS_ADDR 不可达（防火墙、安全组）、或 IP/端口写错"
	default:
		return "请把下方英文 err 复制搜索；并核对 REDIS_ADDR(常用 127.0.0.1:6379)、REDIS_PASSWORD、宝塔 Redis 是否运行"
	}
}

// InitRedis 创建客户端并 Ping。验证码/缓存依赖 Redis，连不上则直接退出。
// 启动前会打印当前使用的 addr/db/是否配置了密码（不打印密码内容），便于与 .env 对照。
func InitRedis(cfg config.RedisConfig) {
	passLabel := "未设置(空)"
	if cfg.Password != "" {
		passLabel = "已设置(非空)"
	}
	log.Printf("Redis 即将连接: addr=%q db=%d password=%s", cfg.Addr, cfg.DB, passLabel)

	Rdb = redis.NewClient(&redis.Options{
		Addr:     cfg.Addr,
		Password: cfg.Password,
		DB:       cfg.DB,
	})
	if err := Rdb.Ping(Ctx).Err(); err != nil {
		log.Printf("Redis Ping 失败，原始错误: %v", err)
		log.Fatalf("Redis 无法连接 → %s", RedisConnectHint(err))
	}
	log.Printf("Redis 已连接 addr=%q db=%d", cfg.Addr, cfg.DB)
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
