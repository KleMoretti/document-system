package internal

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"
)

type UserClaims struct {
	UserID string `json:"sub"`
	Email  string `json:"email"`
	Exp    int64  `json:"exp"`
}

type JWTManager struct {
	secret []byte
	ttl    time.Duration
}

func NewJWTManager(secret string, ttl time.Duration) *JWTManager {
	return &JWTManager{secret: []byte(secret), ttl: ttl}
}

func (m *JWTManager) Sign(claims UserClaims) (string, error) {
	claims.Exp = time.Now().Add(m.ttl).Unix()
	header := map[string]string{"alg": "HS256", "typ": "JWT"}
	headerJSON, err := json.Marshal(header)
	if err != nil {
		return "", err
	}
	claimsJSON, err := json.Marshal(claims)
	if err != nil {
		return "", err
	}

	unsigned := base64.RawURLEncoding.EncodeToString(headerJSON) + "." + base64.RawURLEncoding.EncodeToString(claimsJSON)
	return unsigned + "." + m.signature(unsigned), nil
}

func (m *JWTManager) Verify(token string) (UserClaims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return UserClaims{}, errors.New("invalid token")
	}
	unsigned := parts[0] + "." + parts[1]
	if !hmac.Equal([]byte(parts[2]), []byte(m.signature(unsigned))) {
		return UserClaims{}, errors.New("invalid signature")
	}
	payload, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return UserClaims{}, err
	}
	var claims UserClaims
	if err := json.Unmarshal(payload, &claims); err != nil {
		return UserClaims{}, err
	}
	if claims.Exp < time.Now().Unix() {
		return UserClaims{}, errors.New("token expired")
	}
	return claims, nil
}

func (m *JWTManager) signature(unsigned string) string {
	mac := hmac.New(sha256.New, m.secret)
	mac.Write([]byte(unsigned))
	return base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

func NewID() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return fmt.Sprintf("%d", time.Now().UnixNano())
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:])
}
