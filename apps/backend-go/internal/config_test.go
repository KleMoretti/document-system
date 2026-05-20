package internal

import (
	"testing"
	"time"
)

func TestLoadConfigReadsSecurityTuning(t *testing.T) {
	t.Setenv("JWT_TTL", "45m")
	t.Setenv("BCRYPT_COST", "13")
	t.Setenv("REDIS_TLS", "true")

	cfg := LoadConfig()

	if cfg.JWTTTL != 45*time.Minute {
		t.Fatalf("expected JWT_TTL 45m, got %s", cfg.JWTTTL)
	}
	if cfg.BcryptCost != 13 {
		t.Fatalf("expected BCRYPT_COST 13, got %d", cfg.BcryptCost)
	}
	if !cfg.RedisTLS {
		t.Fatal("expected REDIS_TLS true")
	}
}
