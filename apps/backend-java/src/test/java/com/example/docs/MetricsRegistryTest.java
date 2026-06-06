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
    metrics.observeWebSocketError("SLOW_CLIENT");
    metrics.observeWebSocketBytes(512);
    metrics.observeWebSocketSlowClient();
    metrics.observeWebSocketQueueDepth(7);
    metrics.observeWebSocketBroadcast(25);
    metrics.observeWebSocketPersist(40);
    metrics.observeWebSocketBatch(3);
    metrics.observeWebSocketDisconnect();

    var body = metrics.render();

    assertThat(body)
        .contains("documentation_collab_http_requests_total{method=\"GET\",path=\"/api/documents\",status=\"401\"} 1")
        .contains("documentation_collab_ws_connections_total 1")
        .contains("documentation_collab_ws_connections_active 0")
        .contains("documentation_collab_ws_messages_total{type=\"sync:update\"} 1")
        .contains("documentation_collab_ws_errors_total{code=\"SLOW_CLIENT\"} 1")
        .contains("documentation_collab_ws_message_bytes_total 512")
        .contains("documentation_collab_ws_slow_clients_total 1")
        .contains("documentation_collab_ws_send_queue_depth_max 7")
        .contains("documentation_collab_ws_broadcast_duration_ms_sum 25")
        .contains("documentation_collab_ws_persist_duration_ms_sum 40")
        .contains("documentation_collab_ws_batch_size_sum 3");
  }
}
