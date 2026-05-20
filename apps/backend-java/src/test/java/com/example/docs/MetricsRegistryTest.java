package com.example.docs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetricsRegistryTest {
  @Test
  void renderIncludesHttpAndWebSocketCounters() {
    var metrics = new MetricsRegistry();
    metrics.observeHttpRequest("GET", "/api/documents", 401);
    metrics.observeWebSocketConnect();
    metrics.observeWebSocketMessage("sync:update");
    metrics.observeWebSocketDisconnect();

    var body = metrics.render();

    assertThat(body)
        .contains("documentation_collab_http_requests_total{method=\"GET\",path=\"/api/documents\",status=\"401\"} 1")
        .contains("documentation_collab_ws_connections_total 1")
        .contains("documentation_collab_ws_connections_active 0")
        .contains("documentation_collab_ws_messages_total{type=\"sync:update\"} 1");
  }
}
