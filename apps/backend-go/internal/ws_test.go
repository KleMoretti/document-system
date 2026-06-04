package internal

import (
	"encoding/json"
	"net/http"
	"testing"
)

func TestOriginAllowedRejectsUnconfiguredOrigin(t *testing.T) {
	req, err := http.NewRequest(http.MethodGet, "http://localhost/ws/documents/doc-1", nil)
	if err != nil {
		t.Fatalf("create request: %v", err)
	}
	req.Header.Set("Origin", "https://evil.example")

	if originAllowed(req, "http://localhost:5173") {
		t.Fatal("expected unconfigured origin to be rejected")
	}
}

func TestOriginAllowedAcceptsConfiguredOrigin(t *testing.T) {
	req, err := http.NewRequest(http.MethodGet, "http://localhost/ws/documents/doc-1", nil)
	if err != nil {
		t.Fatalf("create request: %v", err)
	}
	req.Header.Set("Origin", "http://localhost:5173")

	if !originAllowed(req, "http://localhost:5173") {
		t.Fatal("expected configured origin to be accepted")
	}
}

func TestWebSocketTokenPrefersSubprotocolToken(t *testing.T) {
	req, err := http.NewRequest(http.MethodGet, "http://localhost/ws/documents/doc-1?token=query-token", nil)
	if err != nil {
		t.Fatalf("create request: %v", err)
	}
	req.Header.Set("Sec-WebSocket-Protocol", "bearer, header-token")

	if got := websocketToken(req); got != "header-token" {
		t.Fatalf("expected header token, got %q", got)
	}
}

func TestBroadcastRawSendsErrorBeforeClosingSlowClient(t *testing.T) {
	hub := NewHub("test-instance")
	client := &Client{docID: "doc-1", send: make(chan []byte, 1)}
	client.send <- []byte("queued")
	hub.Register(client)

	hub.BroadcastRaw("doc-1", []byte("payload"))

	select {
	case payload, ok := <-client.send:
		if !ok {
			t.Fatal("expected slow client error before close")
		}
		var msg WSMessage
		if err := json.Unmarshal(payload, &msg); err != nil {
			t.Fatalf("decode error message: %v", err)
		}
		if msg.Type != "error" || msg.Code != "SLOW_CLIENT" {
			t.Fatalf("expected SLOW_CLIENT error, got %#v", msg)
		}
	default:
		t.Fatal("expected slow client error")
	}
	if _, ok := <-client.send; ok {
		t.Fatal("expected slow client channel to close after error")
	}
}

func TestUpdateSizeRejectsOversizedUpdate(t *testing.T) {
	if updateAllowed(make([]byte, maxUpdateBytes+1)) {
		t.Fatal("expected oversized update to be rejected")
	}
}

func TestSyncInitMessageIncludesSnapshotBeforeUpdates(t *testing.T) {
	payload, err := json.Marshal(WSMessage{
		Type:        "sync:init",
		DocID:       "doc-1",
		Snapshot:    "snapshot-state",
		SnapshotSeq: 12,
		Updates:     []string{"update-13"},
	})
	if err != nil {
		t.Fatalf("marshal sync init: %v", err)
	}

	var decoded map[string]any
	if err := json.Unmarshal(payload, &decoded); err != nil {
		t.Fatalf("decode sync init: %v", err)
	}

	if decoded["snapshot"] != "snapshot-state" {
		t.Fatalf("expected snapshot in payload, got %v", decoded["snapshot"])
	}
	if decoded["snapshotSeq"] != float64(12) {
		t.Fatalf("expected snapshotSeq 12, got %v", decoded["snapshotSeq"])
	}
}
