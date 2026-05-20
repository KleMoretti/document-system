package com.example.docs;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {
  private final MetricsRegistry metrics;

  public MetricsController(MetricsRegistry metrics) {
    this.metrics = metrics;
  }

  @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
  String metrics() {
    return metrics.render();
  }
}
