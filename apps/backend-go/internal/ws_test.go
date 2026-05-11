package internal

import (
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

func TestBroadcastRawClosesSlowClientInsteadOfDroppingPayload(t *testing.T) {
	hub := NewHub("test-instance")
	client := &Client{docID: "doc-1", send: make(chan []byte)}
	hub.Register(client)

	hub.BroadcastRaw("doc-1", []byte("payload"))

	select {
	case _, ok := <-client.send:
		if ok {
			t.Fatal("expected slow client channel to be closed")
		}
	default:
		t.Fatal("expected slow client to be closed")
	}
}
