package com.example.docs;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final AppRepository repository;

  public HealthController(AppRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/healthz")
  Map<String, String> healthz() {
    return Map.of("status", "ok");
  }

  @GetMapping("/readyz")
  Map<String, String> readyz() {
    try {
      repository.ping();
      return Map.of("status", "ready");
    } catch (RuntimeException ex) {
      throw new NotReadyException("Dependencies are not ready.");
    }
  }
}

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
class NotReadyException extends RuntimeException {
  NotReadyException(String message) {
    super(message);
  }
}
