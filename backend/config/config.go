package config

type Config struct {
	DB    DBConfig
	Redis RedisConfig
	JWT   JWTConfig
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

func Load() *Config {
	return &Config{
		DB: DBConfig{
			Host:     "192.168.100.239",
			Port:     "3306",
			User:     "root",
			Password: "mysql_iCSTZc",
			Name:     "short",
		},
		Redis: RedisConfig{
			Addr:     "192.168.100.239:6379",
			Password: "123123",
			DB:       0,
		},
		JWT: JWTConfig{
			Secret: "short-drama-jwt-secret-2024",
			Expire: 72,
		},
	}
}

func (c *DBConfig) DSN() string {
	return c.User + ":" + c.Password + "@tcp(" + c.Host + ":" + c.Port + ")/" + c.Name + "?charset=utf8mb4&parseTime=True&loc=Local"
}
