package main

import (
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"os"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gorilla/websocket"
)

type wsMessage struct {
	Type        string `json:"type"`
	DisplayName string `json:"displayName,omitempty"`
	Color       string `json:"color,omitempty"`
	Update      string `json:"update,omitempty"`
	Code        string `json:"code,omitempty"`
}

type latencySummary struct {
	Count int
	P50   time.Duration
	P95   time.Duration
	Max   time.Duration
}

const maxWriteDuration = 2 * time.Second

func main() {
	target := flag.String("url", "ws://localhost:18080/ws/documents", "WebSocket document base URL.")
	docID := flag.String("doc-id", "", "Document UUID to connect to.")
	token := flag.String("token", "", "JWT token with access to the document.")
	clients := flag.Int("clients", 50, "Number of concurrent WebSocket clients.")
	duration := flag.Duration("duration", 30*time.Second, "Load test duration.")
	interval := flag.Duration("interval", time.Second, "Message interval per client.")
	mode := flag.String("mode", "presence", "Message mode: presence or update.")
	flag.Parse()

	if *docID == "" || *token == "" {
		log.Fatal("-doc-id and -token are required")
	}
	if *clients <= 0 {
		log.Fatal("-clients must be positive")
	}

	wsURL := documentURL(*target, *docID)
	headers := http.Header{"Sec-WebSocket-Protocol": []string{"bearer, " + *token}}
	deadline := time.Now().Add(*duration)
	writeResults := make(chan time.Duration, *clients*16)
	receiveResults := make(chan time.Duration, *clients*16)
	var sent atomic.Uint64
	var received atomic.Uint64
	var errors atomic.Uint64
	var connected atomic.Uint64
	var disconnects atomic.Uint64
	errorCodes := map[string]uint64{}
	var errorCodesMu sync.Mutex
	errorStages := map[string]uint64{}
	var errorStagesMu sync.Mutex
	recordErrorStage := func(stage string) {
		errorStagesMu.Lock()
		errorStages[stage]++
		errorStagesMu.Unlock()
	}
	var wg sync.WaitGroup

	for i := 0; i < *clients; i++ {
		wg.Add(1)
		go func(index int) {
			defer wg.Done()
			conn, _, err := websocket.DefaultDialer.Dial(wsURL, headers)
			if err != nil {
				errors.Add(1)
				recordErrorStage("dial")
				return
			}
			defer conn.Close()
			connected.Add(1)
			_ = conn.SetReadDeadline(time.Now().Add(5 * time.Second))
			if _, _, err := conn.ReadMessage(); err != nil {
				errors.Add(1)
				recordErrorStage("init")
				return
			}
			_ = conn.SetReadDeadline(time.Time{})

			readerDone := make(chan struct{})
			go func() {
				defer close(readerDone)
				for {
					var incoming wsMessage
					if err := conn.ReadJSON(&incoming); err != nil {
						if time.Now().Before(deadline) {
							disconnects.Add(1)
							recordErrorStage("read")
						}
						return
					}
					received.Add(1)
					if incoming.Type == "error" && incoming.Code != "" {
						errorCodesMu.Lock()
						errorCodes[incoming.Code]++
						errorCodesMu.Unlock()
					}
					if latency, ok := receiveLatency(incoming); ok {
						recordDuration(receiveResults, latency)
					}
					if time.Now().After(deadline) {
						return
					}
				}
			}()

			ticker := time.NewTicker(*interval)
			defer ticker.Stop()
			for time.Now().Before(deadline) {
				<-ticker.C
				if !shouldWriteMessage(time.Now(), deadline) {
					break
				}
				message := messageFor(*mode, index)
				start := time.Now()
				if err := writeJSONWithDeadline(conn, message, deadline, start, maxWriteDuration); err != nil {
					errors.Add(1)
					recordErrorStage("write")
					return
				}
				recordDuration(writeResults, time.Since(start))
				sent.Add(1)
			}
			_ = conn.Close()
			select {
			case <-readerDone:
			case <-time.After(2 * time.Second):
				disconnects.Add(1)
			}
		}(i)
	}

	wg.Wait()
	close(writeResults)
	close(receiveResults)

	writeLatencies := drainDurations(writeResults)
	receiveLatencies := drainDurations(receiveResults)
	errorCodesMu.Lock()
	errorCodeSnapshot := map[string]uint64{}
	for code, count := range errorCodes {
		errorCodeSnapshot[code] = count
	}
	errorCodesMu.Unlock()
	errorStagesMu.Lock()
	errorStageSnapshot := map[string]uint64{}
	for stage, count := range errorStages {
		errorStageSnapshot[stage] = count
	}
	errorStagesMu.Unlock()
	report := buildReport(reportInput{
		Target:         wsURL,
		Clients:        *clients,
		Connected:      connected.Load(),
		Sent:           sent.Load(),
		Received:       received.Load(),
		Errors:         errors.Load(),
		Disconnects:    disconnects.Load(),
		Duration:       *duration,
		WriteLatency:   writeLatencies,
		ReceiveLatency: receiveLatencies,
		ErrorCodes:     errorCodeSnapshot,
		ErrorStages:    errorStageSnapshot,
	})
	encoder := json.NewEncoder(os.Stdout)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(report); err != nil {
		log.Fatal(err)
	}
}

func documentURL(base string, docID string) string {
	trimmed := stringsTrimRightSlash(base)
	parsed, err := url.Parse(trimmed + "/" + docID)
	if err != nil {
		log.Fatal(err)
	}
	return parsed.String()
}

func messageFor(mode string, index int) wsMessage {
	now := time.Now().UnixNano()
	if mode == "update" {
		return wsMessage{Type: "sync:update", Update: base64.StdEncoding.EncodeToString([]byte(fmt.Sprintf("load-%d-%d", index, now)))}
	}
	return wsMessage{Type: "presence:update", DisplayName: fmt.Sprintf("load-user-%d-%d", index, now), Color: "#2563eb"}
}

type websocketJSONWriter interface {
	SetWriteDeadline(time.Time) error
	WriteJSON(any) error
}

func writeJSONWithDeadline(conn websocketJSONWriter, message wsMessage, overallDeadline time.Time, now time.Time, maxDuration time.Duration) error {
	writeDeadline := now.Add(maxDuration)
	if overallDeadline.Before(writeDeadline) {
		writeDeadline = overallDeadline
	}
	if err := conn.SetWriteDeadline(writeDeadline); err != nil {
		return err
	}
	return conn.WriteJSON(message)
}

func recordDuration(values chan<- time.Duration, value time.Duration) {
	select {
	case values <- value:
	default:
	}
}

func shouldWriteMessage(now time.Time, deadline time.Time) bool {
	return now.Before(deadline)
}

type reportInput struct {
	Target         string
	Clients        int
	Connected      uint64
	Sent           uint64
	Received       uint64
	Errors         uint64
	Disconnects    uint64
	Duration       time.Duration
	WriteLatency   []time.Duration
	ReceiveLatency []time.Duration
	ErrorCodes     map[string]uint64
	ErrorStages    map[string]uint64
}

func buildReport(input reportInput) map[string]any {
	writeSummary := summarizeLatencies(input.WriteLatency)
	receiveSummary := summarizeLatencies(input.ReceiveLatency)
	return map[string]any{
		"target":                input.Target,
		"clients":               input.Clients,
		"connected":             input.Connected,
		"sent":                  input.Sent,
		"received":              input.Received,
		"errors":                input.Errors,
		"disconnects":           input.Disconnects,
		"error_codes":           input.ErrorCodes,
		"error_stages":          input.ErrorStages,
		"duration":              input.Duration.String(),
		"latency_count":         writeSummary.Count,
		"latency_p50":           writeSummary.P50.String(),
		"latency_p95":           writeSummary.P95.String(),
		"latency_max":           writeSummary.Max.String(),
		"receive_latency_count": receiveSummary.Count,
		"receive_latency_p50":   receiveSummary.P50.String(),
		"receive_latency_p95":   receiveSummary.P95.String(),
		"receive_latency_max":   receiveSummary.Max.String(),
	}
}

func drainDurations(values <-chan time.Duration) []time.Duration {
	latencies := make([]time.Duration, 0, len(values))
	for latency := range values {
		latencies = append(latencies, latency)
	}
	return latencies
}

func receiveLatency(message wsMessage) (time.Duration, bool) {
	var encoded string
	switch message.Type {
	case "sync:update":
		decoded, err := base64.StdEncoding.DecodeString(message.Update)
		if err != nil {
			return 0, false
		}
		encoded = string(decoded)
	case "presence:update":
		encoded = message.DisplayName
	default:
		return 0, false
	}
	parts := strings.Split(encoded, "-")
	if len(parts) == 0 {
		return 0, false
	}
	sentAt, err := strconv.ParseInt(parts[len(parts)-1], 10, 64)
	if err != nil {
		return 0, false
	}
	return time.Since(time.Unix(0, sentAt)), true
}

func summarizeLatencies(values []time.Duration) latencySummary {
	if len(values) == 0 {
		return latencySummary{}
	}
	sorted := append([]time.Duration(nil), values...)
	sort.Slice(sorted, func(i, j int) bool { return sorted[i] < sorted[j] })
	return latencySummary{
		Count: len(sorted),
		P50:   percentile(sorted, 0.50),
		P95:   percentile(sorted, 0.95),
		Max:   sorted[len(sorted)-1],
	}
}

func percentile(sorted []time.Duration, ratio float64) time.Duration {
	index := int(ratio*float64(len(sorted)) + 0.5)
	if index < 1 {
		index = 1
	}
	if index > len(sorted) {
		index = len(sorted)
	}
	return sorted[index-1]
}

func stringsTrimRightSlash(value string) string {
	for len(value) > 0 && value[len(value)-1] == '/' {
		value = value[:len(value)-1]
	}
	return value
}
