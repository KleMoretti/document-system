package internal

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	HTTPAddr       string
	MySQLHost      string
	MySQLPort      string
	MySQLDatabase  string
	MySQLUser      string
	MySQLPassword  string
	RedisHost      string
	RedisPort      string
	RedisPassword  string
	RedisTLS       bool
	JWTSecret      string
	JWTTTL         time.Duration
	BcryptCost     int
	AllowedOrigins string
	InstanceID     string
}

func LoadConfig() Config {
	return Config{
		HTTPAddr:       env("GO_HTTP_ADDR", ":8081"),
		MySQLHost:      env("MYSQL_HOST", "127.0.0.1"),
		MySQLPort:      env("MYSQL_PORT", "3306"),
		MySQLDatabase:  env("MYSQL_DATABASE", "documentation_collab"),
		MySQLUser:      env("MYSQL_USER", "root"),
		MySQLPassword:  env("MYSQL_PASSWORD", ""),
		RedisHost:      env("REDIS_HOST", "127.0.0.1"),
		RedisPort:      env("REDIS_PORT", "6379"),
		RedisPassword:  env("REDIS_PASSWORD", ""),
		RedisTLS:       envBool("REDIS_TLS", false),
		JWTSecret:      env("JWT_SECRET", ""),
		JWTTTL:         envDuration("JWT_TTL", 2*time.Hour),
		BcryptCost:     envInt("BCRYPT_COST", 12),
		AllowedOrigins: env("ALLOWED_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173"),
		InstanceID:     NewID(),
	}
}

func (c Config) MySQLDSN() string {
	return fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?parseTime=true&multiStatements=true", c.MySQLUser, c.MySQLPassword, c.MySQLHost, c.MySQLPort, c.MySQLDatabase)
}

func (c Config) RedisAddr() string {
	return c.RedisHost + ":" + c.RedisPort
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func envInt(key string, fallback int) int {
	if value := os.Getenv(key); value != "" {
		parsed, err := strconv.Atoi(value)
		if err == nil {
			return parsed
		}
	}
	return fallback
}

func envDuration(key string, fallback time.Duration) time.Duration {
	if value := os.Getenv(key); value != "" {
		parsed, err := time.ParseDuration(value)
		if err == nil {
			return parsed
		}
	}
	return fallback
}

func envBool(key string, fallback bool) bool {
	if value := os.Getenv(key); value != "" {
		parsed, err := strconv.ParseBool(value)
		if err == nil {
			return parsed
		}
	}
	return fallback
}
