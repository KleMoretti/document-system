package internal

import (
	"testing"
	"time"
)

func TestLoadConfigReadsSecurityTuning(t *testing.T) {
	t.Setenv("JWT_TTL", "45m")
	t.Setenv("BCRYPT_COST", "13")
	t.Setenv("REDIS_TLS", "true")
	t.Setenv("DB_MAX_OPEN_CONNS", "40")
	t.Setenv("DB_MAX_IDLE_CONNS", "12")
	t.Setenv("WS_SEND_QUEUE_SIZE", "64")
	t.Setenv("WS_BATCH_MAX_SIZE", "24")
	t.Setenv("WS_BATCH_FLUSH_MS", "30")
	t.Setenv("WS_SNAPSHOT_MIN_UPDATES", "150")

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
	if cfg.DBMaxOpenConns != 40 {
		t.Fatalf("expected DB_MAX_OPEN_CONNS 40, got %d", cfg.DBMaxOpenConns)
	}
	if cfg.DBMaxIdleConns != 12 {
		t.Fatalf("expected DB_MAX_IDLE_CONNS 12, got %d", cfg.DBMaxIdleConns)
	}
	if cfg.WSSendQueueSize != 64 {
		t.Fatalf("expected WS_SEND_QUEUE_SIZE 64, got %d", cfg.WSSendQueueSize)
	}
	if cfg.WSBatchMaxSize != 24 {
		t.Fatalf("expected WS_BATCH_MAX_SIZE 24, got %d", cfg.WSBatchMaxSize)
	}
	if cfg.WSBatchFlush != 30*time.Millisecond {
		t.Fatalf("expected WS_BATCH_FLUSH_MS 30ms, got %s", cfg.WSBatchFlush)
	}
	if cfg.WSSnapshotMinUpdates != 150 {
		t.Fatalf("expected WS_SNAPSHOT_MIN_UPDATES 150, got %d", cfg.WSSnapshotMinUpdates)
	}
}
