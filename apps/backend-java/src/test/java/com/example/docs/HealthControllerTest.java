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
}
