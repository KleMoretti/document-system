package internal

import (
	"fmt"
	"net/http"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
)

type Metrics struct {
	httpMu             sync.RWMutex
	httpRequests       map[string]uint64
	wsMessagesMu       sync.RWMutex
	wsMessages         map[string]uint64
	wsConnectionsTotal atomic.Uint64
	wsConnectionsOpen  atomic.Int64
}

func NewMetrics() *Metrics {
	return &Metrics{
		httpRequests: map[string]uint64{},
		wsMessages:   map[string]uint64{},
	}
}

func (m *Metrics) ObserveHTTPRequest(method string, path string, status int) {
	key := method + "|" + normalizeMetricPath(path) + "|" + fmt.Sprint(status)
	m.httpMu.Lock()
	m.httpRequests[key]++
	m.httpMu.Unlock()
}

func (m *Metrics) ObserveWebSocketConnect() {
	m.wsConnectionsTotal.Add(1)
	m.wsConnectionsOpen.Add(1)
}

func (m *Metrics) ObserveWebSocketDisconnect() {
	m.wsConnectionsOpen.Add(-1)
}

func (m *Metrics) ObserveWebSocketMessage(messageType string) {
	if messageType == "" {
		messageType = "unknown"
	}
	m.wsMessagesMu.Lock()
	m.wsMessages[messageType]++
	m.wsMessagesMu.Unlock()
}

func (m *Metrics) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		recorder := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(recorder, r)
		m.ObserveHTTPRequest(r.Method, r.URL.Path, recorder.status)
	})
}

func (m *Metrics) Render() string {
	var builder strings.Builder
	builder.WriteString("# TYPE documentation_collab_http_requests_total counter\n")
	for _, line := range m.httpLines() {
		builder.WriteString(line)
	}
	builder.WriteString("# TYPE documentation_collab_ws_connections_total counter\n")
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_connections_total %d\n", m.wsConnectionsTotal.Load()))
	builder.WriteString("# TYPE documentation_collab_ws_connections_active gauge\n")
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_connections_active %d\n", m.wsConnectionsOpen.Load()))
	builder.WriteString("# TYPE documentation_collab_ws_messages_total counter\n")
	for _, line := range m.wsMessageLines() {
		builder.WriteString(line)
	}
	return builder.String()
}

func (m *Metrics) httpLines() []string {
	m.httpMu.RLock()
	defer m.httpMu.RUnlock()
	lines := make([]string, 0, len(m.httpRequests))
	for key, count := range m.httpRequests {
		parts := strings.Split(key, "|")
		lines = append(lines, fmt.Sprintf(
			"documentation_collab_http_requests_total{method=%q,path=%q,status=%q} %d\n",
			parts[0],
			parts[1],
			parts[2],
			count,
		))
	}
	sort.Strings(lines)
	return lines
}

func (m *Metrics) wsMessageLines() []string {
	m.wsMessagesMu.RLock()
	defer m.wsMessagesMu.RUnlock()
	lines := make([]string, 0, len(m.wsMessages))
	for messageType, count := range m.wsMessages {
		lines = append(lines, fmt.Sprintf("documentation_collab_ws_messages_total{type=%q} %d\n", messageType, count))
	}
	sort.Strings(lines)
	return lines
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(status int) {
	r.status = status
	r.ResponseWriter.WriteHeader(status)
}

func normalizeMetricPath(path string) string {
	parts := strings.Split(strings.Trim(path, "/"), "/")
	for index, part := range parts {
		if validID(part) {
			parts[index] = "{docId}"
		}
	}
	if len(parts) == 1 && parts[0] == "" {
		return "/"
	}
	return "/" + strings.Join(parts, "/")
}
