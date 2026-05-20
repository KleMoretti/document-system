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
  private final AtomicLong wsConnectionsTotal = new AtomicLong();
  private final AtomicLong wsConnectionsActive = new AtomicLong();

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

  private String normalizePath(String path) {
    return path.replaceAll(
        "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        "/{docId}");
  }
}
