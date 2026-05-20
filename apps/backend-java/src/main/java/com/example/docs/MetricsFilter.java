package com.example.docs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MetricsFilter extends OncePerRequestFilter {
  private final MetricsRegistry metrics;

  public MetricsFilter(MetricsRegistry metrics) {
    this.metrics = metrics;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      filterChain.doFilter(request, response);
    } finally {
      if (!"/metrics".equals(request.getRequestURI())) {
        metrics.observeHttpRequest(request.getMethod(), request.getRequestURI(), response.getStatus());
      }
    }
  }
}
