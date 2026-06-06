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
	wsErrorsMu         sync.RWMutex
	wsErrors           map[string]uint64
	wsConnectionsTotal atomic.Uint64
	wsConnectionsOpen  atomic.Int64
	wsMessageBytes     atomic.Uint64
	wsSlowClients      atomic.Uint64
	wsQueueDepthMax    atomic.Uint64
	wsBroadcastCount   atomic.Uint64
	wsBroadcastSum     atomic.Uint64
	wsBroadcastMax     atomic.Uint64
	wsPersistCount     atomic.Uint64
	wsPersistSum       atomic.Uint64
	wsPersistMax       atomic.Uint64
	wsBatchCount       atomic.Uint64
	wsBatchSum         atomic.Uint64
	wsBatchMax         atomic.Uint64
}

func NewMetrics() *Metrics {
	return &Metrics{
		httpRequests: map[string]uint64{},
		wsMessages:   map[string]uint64{},
		wsErrors:     map[string]uint64{},
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

func (m *Metrics) ObserveWebSocketError(code string) {
	if code == "" {
		code = "UNKNOWN"
	}
	m.wsErrorsMu.Lock()
	m.wsErrors[code]++
	m.wsErrorsMu.Unlock()
}

func (m *Metrics) ObserveWebSocketBytes(bytes int) {
	if bytes > 0 {
		m.wsMessageBytes.Add(uint64(bytes))
	}
}

func (m *Metrics) ObserveWebSocketSlowClient() {
	m.wsSlowClients.Add(1)
}

func (m *Metrics) ObserveWebSocketQueueDepth(depth int) {
	if depth > 0 {
		observeMax(&m.wsQueueDepthMax, uint64(depth))
	}
}

func (m *Metrics) ObserveWebSocketBroadcast(durationMs int64) {
	observeDuration(&m.wsBroadcastCount, &m.wsBroadcastSum, &m.wsBroadcastMax, durationMs)
}

func (m *Metrics) ObserveWebSocketPersist(durationMs int64) {
	observeDuration(&m.wsPersistCount, &m.wsPersistSum, &m.wsPersistMax, durationMs)
}

func (m *Metrics) ObserveWebSocketBatch(size int) {
	if size <= 0 {
		return
	}
	m.wsBatchCount.Add(1)
	m.wsBatchSum.Add(uint64(size))
	observeMax(&m.wsBatchMax, uint64(size))
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
	builder.WriteString("# TYPE documentation_collab_ws_errors_total counter\n")
	for _, line := range m.wsErrorLines() {
		builder.WriteString(line)
	}
	builder.WriteString("# TYPE documentation_collab_ws_message_bytes_total counter\n")
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_message_bytes_total %d\n", m.wsMessageBytes.Load()))
	builder.WriteString("# TYPE documentation_collab_ws_slow_clients_total counter\n")
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_slow_clients_total %d\n", m.wsSlowClients.Load()))
	builder.WriteString("# TYPE documentation_collab_ws_send_queue_depth_max gauge\n")
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_send_queue_depth_max %d\n", m.wsQueueDepthMax.Load()))
	builder.WriteString("# TYPE documentation_collab_ws_broadcast_duration_ms summary\n")
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_broadcast_duration_ms_count %d\n", m.wsBroadcastCount.Load()))
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_broadcast_duration_ms_sum %d\n", m.wsBroadcastSum.Load()))
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_broadcast_duration_ms_max %d\n", m.wsBroadcastMax.Load()))
	builder.WriteString("# TYPE documentation_collab_ws_persist_duration_ms summary\n")
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_persist_duration_ms_count %d\n", m.wsPersistCount.Load()))
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_persist_duration_ms_sum %d\n", m.wsPersistSum.Load()))
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_persist_duration_ms_max %d\n", m.wsPersistMax.Load()))
	builder.WriteString("# TYPE documentation_collab_ws_batch_size summary\n")
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_batch_size_count %d\n", m.wsBatchCount.Load()))
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_batch_size_sum %d\n", m.wsBatchSum.Load()))
	builder.WriteString(fmt.Sprintf("documentation_collab_ws_batch_size_max %d\n", m.wsBatchMax.Load()))
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

func (m *Metrics) wsErrorLines() []string {
	m.wsErrorsMu.RLock()
	defer m.wsErrorsMu.RUnlock()
	lines := make([]string, 0, len(m.wsErrors))
	for code, count := range m.wsErrors {
		lines = append(lines, fmt.Sprintf("documentation_collab_ws_errors_total{code=%q} %d\n", code, count))
	}
	sort.Strings(lines)
	return lines
}

func observeDuration(count *atomic.Uint64, sum *atomic.Uint64, max *atomic.Uint64, durationMs int64) {
	if durationMs < 0 {
		durationMs = 0
	}
	count.Add(1)
	sum.Add(uint64(durationMs))
	observeMax(max, uint64(durationMs))
}

func observeMax(target *atomic.Uint64, value uint64) {
	for {
		current := target.Load()
		if value <= current || target.CompareAndSwap(current, value) {
			return
		}
	}
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
