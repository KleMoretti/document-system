package com.example.docs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MetricsRegistry {
  private final Map<String, AtomicLong> httpRequests = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> wsMessages = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> wsErrors = new ConcurrentHashMap<>();
  private final AtomicLong wsConnectionsTotal = new AtomicLong();
  private final AtomicLong wsConnectionsActive = new AtomicLong();
  private final AtomicLong wsMessageBytes = new AtomicLong();
  private final AtomicLong wsSlowClients = new AtomicLong();
  private final AtomicLong wsQueueDepthMax = new AtomicLong();
  private final AtomicLong wsBroadcastCount = new AtomicLong();
  private final AtomicLong wsBroadcastSum = new AtomicLong();
  private final AtomicLong wsBroadcastMax = new AtomicLong();
  private final AtomicLong wsPersistCount = new AtomicLong();
  private final AtomicLong wsPersistSum = new AtomicLong();
  private final AtomicLong wsPersistMax = new AtomicLong();
  private final AtomicLong wsBatchCount = new AtomicLong();
  private final AtomicLong wsBatchSum = new AtomicLong();
  private final AtomicLong wsBatchMax = new AtomicLong();

  void observeHttpRequest(String method, String path, int status) {
    var key = method + "|" + normalizePath(path) + "|" + status;
    httpRequests.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
  }

  void observeWebSocketConnect() {
    wsConnectionsTotal.incrementAndGet();
    wsConnectionsActive.incrementAndGet();
  }

  void observeWebSocketDisconnect() {
    wsConnectionsActive.decrementAndGet();
  }

  void observeWebSocketMessage(String type) {
    var safeType = type == null || type.isBlank() ? "unknown" : type;
    wsMessages.computeIfAbsent(safeType, ignored -> new AtomicLong()).incrementAndGet();
  }

  void observeWebSocketError(String code) {
    var safeCode = code == null || code.isBlank() ? "UNKNOWN" : code;
    wsErrors.computeIfAbsent(safeCode, ignored -> new AtomicLong()).incrementAndGet();
  }

  void observeWebSocketBytes(long bytes) {
    if (bytes > 0) {
      wsMessageBytes.addAndGet(bytes);
    }
  }

  void observeWebSocketSlowClient() {
    wsSlowClients.incrementAndGet();
  }

  void observeWebSocketQueueDepth(long depth) {
    observeMax(wsQueueDepthMax, depth);
  }

  void observeWebSocketBroadcast(long durationMs) {
    observeDuration(wsBroadcastCount, wsBroadcastSum, wsBroadcastMax, durationMs);
  }

  void observeWebSocketPersist(long durationMs) {
    observeDuration(wsPersistCount, wsPersistSum, wsPersistMax, durationMs);
  }

  void observeWebSocketBatch(long size) {
    if (size <= 0) {
      return;
    }
    wsBatchCount.incrementAndGet();
    wsBatchSum.addAndGet(size);
    observeMax(wsBatchMax, size);
  }

  String render() {
    var builder = new StringBuilder();
    builder.append("# TYPE documentation_collab_http_requests_total counter\n");
    for (String line : httpLines()) {
      builder.append(line);
    }
    builder.append("# TYPE documentation_collab_ws_connections_total counter\n");
    builder.append("documentation_collab_ws_connections_total ").append(wsConnectionsTotal.get()).append('\n');
    builder.append("# TYPE documentation_collab_ws_connections_active gauge\n");
    builder.append("documentation_collab_ws_connections_active ").append(wsConnectionsActive.get()).append('\n');
    builder.append("# TYPE documentation_collab_ws_messages_total counter\n");
    for (String line : wsLines()) {
      builder.append(line);
    }
    builder.append("# TYPE documentation_collab_ws_errors_total counter\n");
    for (String line : wsErrorLines()) {
      builder.append(line);
    }
    builder.append("# TYPE documentation_collab_ws_message_bytes_total counter\n");
    builder.append("documentation_collab_ws_message_bytes_total ").append(wsMessageBytes.get()).append('\n');
    builder.append("# TYPE documentation_collab_ws_slow_clients_total counter\n");
    builder.append("documentation_collab_ws_slow_clients_total ").append(wsSlowClients.get()).append('\n');
    builder.append("# TYPE documentation_collab_ws_send_queue_depth_max gauge\n");
    builder.append("documentation_collab_ws_send_queue_depth_max ").append(wsQueueDepthMax.get()).append('\n');
    builder.append("# TYPE documentation_collab_ws_broadcast_duration_ms summary\n");
    builder.append("documentation_collab_ws_broadcast_duration_ms_count ").append(wsBroadcastCount.get()).append('\n');
    builder.append("documentation_collab_ws_broadcast_duration_ms_sum ").append(wsBroadcastSum.get()).append('\n');
    builder.append("documentation_collab_ws_broadcast_duration_ms_max ").append(wsBroadcastMax.get()).append('\n');
    builder.append("# TYPE documentation_collab_ws_persist_duration_ms summary\n");
    builder.append("documentation_collab_ws_persist_duration_ms_count ").append(wsPersistCount.get()).append('\n');
    builder.append("documentation_collab_ws_persist_duration_ms_sum ").append(wsPersistSum.get()).append('\n');
    builder.append("documentation_collab_ws_persist_duration_ms_max ").append(wsPersistMax.get()).append('\n');
    builder.append("# TYPE documentation_collab_ws_batch_size summary\n");
    builder.append("documentation_collab_ws_batch_size_count ").append(wsBatchCount.get()).append('\n');
    builder.append("documentation_collab_ws_batch_size_sum ").append(wsBatchSum.get()).append('\n');
    builder.append("documentation_collab_ws_batch_size_max ").append(wsBatchMax.get()).append('\n');
    return builder.toString();
  }

  private ArrayList<String> httpLines() {
    var lines = new ArrayList<String>();
    httpRequests.forEach(
        (key, count) -> {
          var parts = key.split("\\|", -1);
          lines.add(
              "documentation_collab_http_requests_total{method=\""
                  + parts[0]
                  + "\",path=\""
                  + parts[1]
                  + "\",status=\""
                  + parts[2]
                  + "\"} "
                  + count.get()
                  + "\n");
        });
    Collections.sort(lines);
    return lines;
  }

  private ArrayList<String> wsLines() {
    var lines = new ArrayList<String>();
    wsMessages.forEach(
        (type, count) ->
            lines.add("documentation_collab_ws_messages_total{type=\"" + type + "\"} " + count.get() + "\n"));
    Collections.sort(lines);
    return lines;
  }

  private ArrayList<String> wsErrorLines() {
    var lines = new ArrayList<String>();
    wsErrors.forEach(
        (code, count) ->
            lines.add("documentation_collab_ws_errors_total{code=\"" + code + "\"} " + count.get() + "\n"));
    Collections.sort(lines);
    return lines;
  }

  private void observeDuration(AtomicLong count, AtomicLong sum, AtomicLong max, long durationMs) {
    var safeDuration = Math.max(0, durationMs);
    count.incrementAndGet();
    sum.addAndGet(safeDuration);
    observeMax(max, safeDuration);
  }

  private void observeMax(AtomicLong target, long value) {
    if (value <= 0) {
      return;
    }
    long current;
    do {
      current = target.get();
      if (value <= current) {
        return;
      }
    } while (!target.compareAndSet(current, value));
  }

  private String normalizePath(String path) {
    return path.replaceAll(
        "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        "/{docId}");
  }
}
