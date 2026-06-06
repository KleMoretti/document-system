package internal

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestMetricsRenderIncludesHttpAndWebSocketCounters(t *testing.T) {
	metrics := NewMetrics()
	metrics.ObserveHTTPRequest("GET", "/api/documents", http.StatusUnauthorized)
	metrics.ObserveWebSocketConnect()
	metrics.ObserveWebSocketMessage("sync:update")
	metrics.ObserveWebSocketError("SLOW_CLIENT")
	metrics.ObserveWebSocketBytes(512)
	metrics.ObserveWebSocketSlowClient()
	metrics.ObserveWebSocketQueueDepth(7)
	metrics.ObserveWebSocketBroadcast(25)
	metrics.ObserveWebSocketPersist(40)
	metrics.ObserveWebSocketBatch(3)
	metrics.ObserveWebSocketDisconnect()

	body := metrics.Render()

	for _, expected := range []string{
		`documentation_collab_http_requests_total{method="GET",path="/api/documents",status="401"} 1`,
		`documentation_collab_ws_connections_total 1`,
		`documentation_collab_ws_connections_active 0`,
		`documentation_collab_ws_messages_total{type="sync:update"} 1`,
		`documentation_collab_ws_errors_total{code="SLOW_CLIENT"} 1`,
		`documentation_collab_ws_message_bytes_total 512`,
		`documentation_collab_ws_slow_clients_total 1`,
		`documentation_collab_ws_send_queue_depth_max 7`,
		`documentation_collab_ws_broadcast_duration_ms_sum 25`,
		`documentation_collab_ws_persist_duration_ms_sum 40`,
		`documentation_collab_ws_batch_size_sum 3`,
	} {
		if !strings.Contains(body, expected) {
			t.Fatalf("expected metrics output to contain %q, got:\n%s", expected, body)
		}
	}
}

func TestMetricsMiddlewareRecordsStatusCode(t *testing.T) {
	metrics := NewMetrics()
	handler := metrics.Middleware(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusCreated)
	}))

	handler.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodPost, "/api/documents", nil))

	if !strings.Contains(metrics.Render(), `documentation_collab_http_requests_total{method="POST",path="/api/documents",status="201"} 1`) {
		t.Fatalf("expected middleware to record request status, got:\n%s", metrics.Render())
	}
}
