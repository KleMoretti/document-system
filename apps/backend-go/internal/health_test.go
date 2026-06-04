package internal

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHealthEndpointReturnsOK(t *testing.T) {
	server := NewServer(Config{}, nil, nil, nil, nil)
	recorder := httptest.NewRecorder()

	server.Routes().ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/healthz", nil))

	if recorder.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", recorder.Code)
	}
	if recorder.Body.String() != "{\"status\":\"ok\"}\n" {
		t.Fatalf("unexpected health body: %s", recorder.Body.String())
	}
}
