package internal

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"log"

	"github.com/redis/go-redis/v9"
)

type RedisEnvelope struct {
	Source string          `json:"source"`
	DocID  string          `json:"docId"`
	Body   json.RawMessage `json:"body"`
}

type RedisBus struct {
	cfg    Config
	hub    *Hub
	client *redis.Client
}

func NewRedisBus(cfg Config, hub *Hub) *RedisBus {
	options := &redis.Options{
		Addr:     cfg.RedisAddr(),
		Password: cfg.RedisPassword,
	}
	if cfg.RedisTLS {
		options.TLSConfig = &tls.Config{MinVersion: tls.VersionTLS12}
	}
	return &RedisBus{
		cfg:    cfg,
		hub:    hub,
		client: redis.NewClient(options),
	}
}

func (b *RedisBus) Run(ctx context.Context) {
	pubsub := b.client.PSubscribe(ctx, "doc:*")
	defer pubsub.Close()
	for message := range pubsub.Channel() {
		var envelope RedisEnvelope
		if err := json.Unmarshal([]byte(message.Payload), &envelope); err != nil {
			log.Printf("redis payload ignored: %v", err)
			continue
		}
		if envelope.Source == b.cfg.InstanceID {
			continue
		}
		b.hub.BroadcastRaw(envelope.DocID, envelope.Body)
	}
}

func (b *RedisBus) Publish(ctx context.Context, docID string, body []byte) {
	envelope, err := json.Marshal(RedisEnvelope{Source: b.cfg.InstanceID, DocID: docID, Body: body})
	if err != nil {
		log.Printf("redis envelope marshal failed: %v", err)
		return
	}
	if err := b.client.Publish(ctx, "doc:"+docID, envelope).Err(); err != nil {
		log.Printf("redis publish failed: %v", err)
	}
}
