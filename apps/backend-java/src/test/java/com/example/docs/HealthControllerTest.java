package com.example.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {
  @Test
  void healthzReturnsOk() {
    var controller = new HealthController(mock(JdbcTemplate.class));

    assertThat(controller.healthz()).containsEntry("status", "ok");
  }

  @Test
  void readyzReturnsReadyWhenPingSucceeds() {
    var jdbc = mock(JdbcTemplate.class);
    org.mockito.Mockito.when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    var controller = new HealthController(jdbc);

    var response = controller.readyz();
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsEntry("status", "ready");
  }

  @Test
  void readyzReturnsNotReadyWith503WhenPingFails() {
    var jdbc = mock(JdbcTemplate.class);
    org.mockito.Mockito.when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new RuntimeException("boom"));
    var controller = new HealthController(jdbc);

    var response = controller.readyz();
    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody()).containsEntry("status", "not_ready");
  }
}
