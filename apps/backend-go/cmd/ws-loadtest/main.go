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
}

type latencySummary struct {
	Count int
	P50   time.Duration
	P95   time.Duration
	Max   time.Duration
}

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
	results := make(chan time.Duration, *clients*16)
	var sent atomic.Uint64
	var errors atomic.Uint64
	var connected atomic.Uint64
	var wg sync.WaitGroup

	for i := 0; i < *clients; i++ {
		wg.Add(1)
		go func(index int) {
			defer wg.Done()
			conn, _, err := websocket.DefaultDialer.Dial(wsURL, headers)
			if err != nil {
				errors.Add(1)
				return
			}
			defer conn.Close()
			connected.Add(1)
			_ = conn.SetReadDeadline(time.Now().Add(5 * time.Second))
			_, _, _ = conn.ReadMessage()

			ticker := time.NewTicker(*interval)
			defer ticker.Stop()
			for time.Now().Before(deadline) {
				<-ticker.C
				message := messageFor(*mode, index)
				start := time.Now()
				if err := conn.WriteJSON(message); err != nil {
					errors.Add(1)
					return
				}
				results <- time.Since(start)
				sent.Add(1)
			}
		}(i)
	}

	wg.Wait()
	close(results)

	latencies := make([]time.Duration, 0, len(results))
	for latency := range results {
		latencies = append(latencies, latency)
	}
	summary := summarizeLatencies(latencies)
	report := map[string]any{
		"target":        wsURL,
		"clients":       *clients,
		"connected":     connected.Load(),
		"sent":          sent.Load(),
		"errors":        errors.Load(),
		"duration":      duration.String(),
		"latency_count": summary.Count,
		"latency_p50":   summary.P50.String(),
		"latency_p95":   summary.P95.String(),
		"latency_max":   summary.Max.String(),
	}
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
	if mode == "update" {
		return wsMessage{Type: "sync:update", Update: base64.StdEncoding.EncodeToString([]byte(fmt.Sprintf("load-%d", index)))}
	}
	return wsMessage{Type: "presence:update", DisplayName: fmt.Sprintf("load-user-%d", index), Color: "#2563eb"}
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
