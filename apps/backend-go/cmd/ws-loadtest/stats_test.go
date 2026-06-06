package main

import (
	"testing"
	"time"
)

func TestLatencySummaryCalculatesPercentiles(t *testing.T) {
	summary := summarizeLatencies([]time.Duration{
		10 * time.Millisecond,
		20 * time.Millisecond,
		30 * time.Millisecond,
		40 * time.Millisecond,
		50 * time.Millisecond,
	})

	if summary.Count != 5 {
		t.Fatalf("expected count 5, got %d", summary.Count)
	}
	if summary.P50 != 30*time.Millisecond {
		t.Fatalf("expected p50 30ms, got %s", summary.P50)
	}
	if summary.P95 != 50*time.Millisecond {
		t.Fatalf("expected p95 50ms, got %s", summary.P95)
	}
}

func TestLoadReportIncludesReceiveAndErrorStatistics(t *testing.T) {
	report := buildReport(reportInput{
		Target:      "ws://localhost/ws/documents/doc-1",
		Clients:     2,
		Connected:   2,
		Sent:        4,
		Received:    6,
		Errors:      1,
		Disconnects: 1,
		Duration:    30 * time.Second,
		WriteLatency: []time.Duration{
			10 * time.Millisecond,
			20 * time.Millisecond,
		},
		ReceiveLatency: []time.Duration{
			15 * time.Millisecond,
			25 * time.Millisecond,
		},
		ErrorCodes: map[string]uint64{"SLOW_CLIENT": 1},
		ErrorStages: map[string]uint64{
			"dial":  1,
			"write": 2,
		},
	})

	if report["received"] != uint64(6) {
		t.Fatalf("expected received count, got %#v", report["received"])
	}
	if report["disconnects"] != uint64(1) {
		t.Fatalf("expected disconnect count, got %#v", report["disconnects"])
	}
	if report["receive_latency_p95"] != "25ms" {
		t.Fatalf("expected receive p95, got %#v", report["receive_latency_p95"])
	}
	errorCodes, ok := report["error_codes"].(map[string]uint64)
	if !ok || errorCodes["SLOW_CLIENT"] != 1 {
		t.Fatalf("expected error code counts, got %#v", report["error_codes"])
	}
	errorStages, ok := report["error_stages"].(map[string]uint64)
	if !ok || errorStages["dial"] != 1 || errorStages["write"] != 2 {
		t.Fatalf("expected error stage counts, got %#v", report["error_stages"])
	}
}

func TestWriteJSONWithDeadlineUsesSoonerDeadline(t *testing.T) {
	conn := &deadlineRecordingConn{}
	now := time.Unix(100, 0)
	overallDeadline := now.Add(10 * time.Second)

	err := writeJSONWithDeadline(conn, wsMessage{Type: "presence:update"}, overallDeadline, now, 2*time.Second)

	if err != nil {
		t.Fatalf("write json: %v", err)
	}
	if len(conn.deadlines) != 1 {
		t.Fatalf("expected one write deadline, got %d", len(conn.deadlines))
	}
	if want := now.Add(2 * time.Second); !conn.deadlines[0].Equal(want) {
		t.Fatalf("expected per-write deadline %s, got %s", want, conn.deadlines[0])
	}

	conn = &deadlineRecordingConn{}
	overallDeadline = now.Add(time.Second)
	err = writeJSONWithDeadline(conn, wsMessage{Type: "presence:update"}, overallDeadline, now, 2*time.Second)

	if err != nil {
		t.Fatalf("write json: %v", err)
	}
	if want := overallDeadline; !conn.deadlines[0].Equal(want) {
		t.Fatalf("expected overall deadline %s, got %s", want, conn.deadlines[0])
	}
}

func TestRecordDurationDoesNotBlockWhenChannelIsFull(t *testing.T) {
	values := make(chan time.Duration, 1)
	values <- time.Second

	done := make(chan struct{})
	go func() {
		recordDuration(values, 2*time.Second)
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(100 * time.Millisecond):
		t.Fatal("recordDuration blocked on a full channel")
	}
	if len(values) != 1 {
		t.Fatalf("expected full channel to remain at one value, got %d", len(values))
	}
}

func TestShouldWriteMessageRequiresTimeBeforeDeadline(t *testing.T) {
	deadline := time.Unix(100, 0)

	if !shouldWriteMessage(deadline.Add(-time.Nanosecond), deadline) {
		t.Fatal("expected writes before the deadline to be allowed")
	}
	if shouldWriteMessage(deadline, deadline) {
		t.Fatal("expected writes at the deadline to be skipped")
	}
	if shouldWriteMessage(deadline.Add(time.Nanosecond), deadline) {
		t.Fatal("expected writes after the deadline to be skipped")
	}
}

type deadlineRecordingConn struct {
	deadlines []time.Time
}

func (c *deadlineRecordingConn) SetWriteDeadline(deadline time.Time) error {
	c.deadlines = append(c.deadlines, deadline)
	return nil
}

func (c *deadlineRecordingConn) WriteJSON(any) error {
	return nil
}
