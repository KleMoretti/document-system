package internal

import (
	"context"
	"errors"
	"sync"
	"time"
)

type UpdateBatcherConfig struct {
	MaxSize  int
	FlushAge time.Duration
	Append   func(context.Context, string, [][]byte) error
	Metrics  *Metrics
}

type UpdateBatcher struct {
	cfg    UpdateBatcherConfig
	mu     sync.Mutex
	queues map[string]*documentUpdateQueue
	closed bool
}

type documentUpdateQueue struct {
	items []pendingDocumentUpdate
	timer *time.Timer
}

type pendingDocumentUpdate struct {
	update []byte
	done   chan error
}

func NewUpdateBatcher(cfg UpdateBatcherConfig) *UpdateBatcher {
	if cfg.MaxSize <= 0 {
		cfg.MaxSize = 32
	}
	if cfg.FlushAge <= 0 {
		cfg.FlushAge = 25 * time.Millisecond
	}
	return &UpdateBatcher{cfg: cfg, queues: map[string]*documentUpdateQueue{}}
}

func (b *UpdateBatcher) Append(ctx context.Context, docID string, update []byte) error {
	if b == nil || b.cfg.Append == nil {
		return errors.New("update batcher is not configured")
	}
	done := make(chan error, 1)
	item := pendingDocumentUpdate{update: append([]byte(nil), update...), done: done}
	flushNow := false

	b.mu.Lock()
	if b.closed {
		b.mu.Unlock()
		return errors.New("update batcher is closed")
	}
	queue := b.queues[docID]
	if queue == nil {
		queue = &documentUpdateQueue{}
		b.queues[docID] = queue
	}
	queue.items = append(queue.items, item)
	if b.cfg.Metrics != nil {
		b.cfg.Metrics.ObserveWebSocketQueueDepth(len(queue.items))
	}
	if len(queue.items) >= b.cfg.MaxSize {
		if queue.timer != nil {
			queue.timer.Stop()
			queue.timer = nil
		}
		flushNow = true
	} else if queue.timer == nil {
		queue.timer = time.AfterFunc(b.cfg.FlushAge, func() {
			b.flush(docID)
		})
	}
	b.mu.Unlock()

	if flushNow {
		go b.flush(docID)
	}

	select {
	case err := <-done:
		return err
	case <-ctx.Done():
		return ctx.Err()
	}
}

func (b *UpdateBatcher) Close() {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.closed = true
	for _, queue := range b.queues {
		if queue.timer != nil {
			queue.timer.Stop()
		}
		for _, item := range queue.items {
			item.done <- errors.New("update batcher is closed")
		}
	}
	b.queues = map[string]*documentUpdateQueue{}
}

func (b *UpdateBatcher) flush(docID string) {
	b.mu.Lock()
	queue := b.queues[docID]
	if queue == nil || len(queue.items) == 0 {
		b.mu.Unlock()
		return
	}
	items := queue.items
	queue.items = nil
	if queue.timer != nil {
		queue.timer.Stop()
		queue.timer = nil
	}
	delete(b.queues, docID)
	b.mu.Unlock()

	updates := make([][]byte, 0, len(items))
	for _, item := range items {
		updates = append(updates, item.update)
	}
	start := time.Now()
	err := b.cfg.Append(context.Background(), docID, updates)
	if b.cfg.Metrics != nil {
		b.cfg.Metrics.ObserveWebSocketPersist(time.Since(start).Milliseconds())
		b.cfg.Metrics.ObserveWebSocketBatch(len(updates))
	}
	for _, item := range items {
		item.done <- err
	}
}
