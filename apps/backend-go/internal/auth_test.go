package internal

import (
	"testing"
	"time"
)

func TestJWTManagerSignsAndVerifiesToken(t *testing.T) {
	manager := NewJWTManager("test-secret", time.Hour)

	token, err := manager.Sign(UserClaims{UserID: "user-1", Email: "ada@example.com"})
	if err != nil {
		t.Fatalf("sign token: %v", err)
	}

	claims, err := manager.Verify(token)
	if err != nil {
		t.Fatalf("verify token: %v", err)
	}

	if claims.UserID != "user-1" {
		t.Fatalf("expected user-1, got %s", claims.UserID)
	}
	if claims.Email != "ada@example.com" {
		t.Fatalf("expected email to round-trip, got %s", claims.Email)
	}
}

func TestJWTManagerRejectsTamperedToken(t *testing.T) {
	manager := NewJWTManager("test-secret", time.Hour)

	token, err := manager.Sign(UserClaims{UserID: "user-1", Email: "ada@example.com"})
	if err != nil {
		t.Fatalf("sign token: %v", err)
	}

	_, err = manager.Verify(token + "x")
	if err == nil {
		t.Fatal("expected tampered token to fail verification")
	}
}
