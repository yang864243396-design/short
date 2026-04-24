package config

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

type Config struct {
	DB                   DBConfig
	Redis                RedisConfig
	JWT                  JWTConfig
	SMTP                 SMTPConfig
	UserRechargeSimulate bool // USER_RECHARGE_SIMULATE：为 true 时允许客户端调用模拟支付完成入账（仅测试）
	// 聚合（lbzf/twowg）开关与密钥；LUBZF_ENABLED 为 true 时走统一下单+回调+查单
	LubzfEnabled  bool
	LubzfMchID    string
	LubzfSignKey  string
	LubzfAPIBase  string
	APIPublicBase string // 可选。若为空，统一下单时从当前请求 Host+协议推导（与客户端访问域名一致；反代需 X-Forwarded-Proto 等）
	// PayNotifyLogFile 支付异步回调专用日志文件路径（追加）。环境变量 PAY_NOTIFY_LOG_FILE，默认 logs/pay_notify.log（相对进程工作目录）。
	PayNotifyLogFile string

	// StreamSignedURLMinutes 拉流 HMAC 直链有效期（分钟），默认 30；STREAM_SIGNED_URL_MINUTES
	StreamSignedURLMinutes int
	// StreamIPRequestsPerMinute 单 IP 每分钟对 /api/v1/stream 的最大请求数（Gin 中间件），默认 120；STREAM_RATE_PER_MINUTE
	StreamIPRequestsPerMinute int
}

// LubzfNotifyIPWhitelist 聚合异步通知（LubzfNotify）允许的 ClientIP 列表；nil/空=不限制。
// 未设置 LUBZF_NOTIFY_ALLOW_IPS 时默认仅允许台方回调 IP 34.64.72.169；设为 * 表示不校验 IP。
func (c *Config) LubzfNotifyIPWhitelist() []string {
	if c == nil {
		return []string{"34.64.72.169"}
	}
	v, has := os.LookupEnv("LUBZF_NOTIFY_ALLOW_IPS")
	if !has {
		return []string{"34.64.72.169"}
	}
	v = strings.TrimSpace(v)
	if v == "" || v == "*" {
		return nil
	}
	parts := strings.Split(v, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			out = append(out, p)
		}
	}
	if len(out) == 0 {
		return nil
	}
	return out
}

// SMTPConfig 邮件发送（注册验证码等），由环境变量或 .env 中 SMTP_* 提供
type SMTPConfig struct {
	Host     string
	Port     int
	User     string
	Password string
	From     string
}

type DBConfig struct {
	Host     string
	Port     string
	User     string
	Password string
	Name     string
}

type RedisConfig struct {
	Addr     string
	Password string
	DB       int
}

type JWTConfig struct {
	Secret string
	Expire int // hours
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func getenvInt(key string, def int) int {
	s := os.Getenv(key)
	if s == "" {
		return def
	}
	v, err := strconv.Atoi(s)
	if err != nil {
		return def
	}
	return v
}

// redisPasswordFromEnv 必须用 LookupEnv：.env 里写了 REDIS_PASSWORD=（空）表示「无密码」，
// 若用 getenv(..., "123123")，空串会落到默认值，导致无密码 Redis 仍去 AUTH。
func redisPasswordFromEnv() string {
	v, ok := os.LookupEnv("REDIS_PASSWORD")
	if !ok {
		return "123123"
	}
	return strings.TrimSpace(v)
}

// Load 从环境变量读取配置；未设置时使用下方默认值（本地开发）。
// 测试 / 生产服务器请在系统环境或 backend/.env 中设置 DB_*、REDIS_*、JWT_SECRET 等。
func envBool(key string) bool {
	v := strings.ToLower(strings.TrimSpace(os.Getenv(key)))
	return v == "1" || v == "true" || v == "yes"
}

func Load() *Config {
	smtpPort := getenvInt("SMTP_PORT", 465)
	if smtpPort <= 0 {
		smtpPort = 465
	}
	return &Config{
		UserRechargeSimulate: envBool("USER_RECHARGE_SIMULATE"),
		LubzfEnabled:         envBool("LUBZF_ENABLED"),
		LubzfMchID:           strings.TrimSpace(getenv("LUBZF_MCH_ID", "")),
		LubzfSignKey:         strings.TrimSpace(getenv("LUBZF_SIGN_KEY", "")),
		LubzfAPIBase:         strings.TrimSpace(getenv("LUBZF_API_BASE", "https://twowg.lbzf.xyz/api/pay")),
		APIPublicBase:        strings.TrimSpace(getenv("API_PUBLIC_BASE", "")),
		PayNotifyLogFile:          strings.TrimSpace(getenv("PAY_NOTIFY_LOG_FILE", "logs/pay_notify.log")),
		StreamSignedURLMinutes:    getenvInt("STREAM_SIGNED_URL_MINUTES", 30),
		StreamIPRequestsPerMinute: getenvInt("STREAM_RATE_PER_MINUTE", 120),
		DB: DBConfig{
			Host:     getenv("DB_HOST", "192.168.100.239"),
			Port:     getenv("DB_PORT", "3306"),
			User:     getenv("DB_USER", "root"),
			Password: getenv("DB_PASSWORD", "mysql_iCSTZc"),
			Name:     getenv("DB_NAME", "short"),
		},
		Redis: RedisConfig{
			Addr:     getenv("REDIS_ADDR", "192.168.100.239:6379"),
			Password: redisPasswordFromEnv(),
			DB:       getenvInt("REDIS_DB", 0),
		},
		JWT: JWTConfig{
			Secret: getenv("JWT_SECRET", "short-drama-jwt-secret-2024"),
			Expire: getenvInt("JWT_EXPIRE_HOURS", 72),
		},
		SMTP: SMTPConfig{
			Host:     os.Getenv("SMTP_HOST"),
			Port:     smtpPort,
			User:     os.Getenv("SMTP_USER"),
			Password: os.Getenv("SMTP_PASSWORD"),
			From:     os.Getenv("SMTP_FROM"),
		},
	}
}

// PayNotifyLogPathDisplay 启动时打印：将配置路径解析为绝对路径（与当前工作目录有关）。
func (c *Config) PayNotifyLogPathDisplay() string {
	p := "logs/pay_notify.log"
	if c != nil && strings.TrimSpace(c.PayNotifyLogFile) != "" {
		p = strings.TrimSpace(c.PayNotifyLogFile)
	}
	abs, err := filepath.Abs(p)
	if err != nil {
		return p
	}
	return abs
}

func (c *DBConfig) DSN() string {
	return c.User + ":" + c.Password + "@tcp(" + c.Host + ":" + c.Port + ")/" + c.Name + "?charset=utf8mb4&parseTime=True&loc=Local"
}

// ListenAddr 监听地址，默认 :8080。可用 HTTP_PORT 或 PORT（云主机常用 PORT）。
func ListenAddr() string {
	p := getenv("HTTP_PORT", "")
	if p == "" {
		p = os.Getenv("PORT")
	}
	if p == "" {
		p = "8080"
	}
	p = strings.TrimSpace(p)
	if strings.HasPrefix(p, ":") {
		return p
	}
	return ":" + p
}
