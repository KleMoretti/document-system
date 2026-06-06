package com.example.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class HealthControllerTest {
  @Test
  void healthzReturnsOk() {
    var controller = new HealthController(mock(AppRepository.class));

    assertThat(controller.healthz()).containsEntry("status", "ok");
  }

  @Test
  void readyzReturnsReadyWhenPingSucceeds() {
    var repository = mock(AppRepository.class);
    var controller = new HealthController(repository);

    var response = controller.readyz();
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsEntry("status", "ready");
  }

  @Test
  void readyzReturnsNotReadyWith503WhenPingFails() {
    var repository = mock(AppRepository.class);
    org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(repository).ping();
    var controller = new HealthController(repository);

    var response = controller.readyz();
    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody()).containsEntry("status", "not_ready");
  }
}
