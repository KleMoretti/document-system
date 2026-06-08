package com.example.docs;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnRole({ServiceRole.REALTIME, ServiceRole.ALL})
public class WebSocketConfig implements WebSocketConfigurer {
  private final DocumentSocketHandler handler;
  private final String[] allowedOrigins;

  public WebSocketConfig(
      DocumentSocketHandler handler, @Value("${app.allowed-origins}") String allowedOrigins) {
    this.handler = handler;
    this.allowedOrigins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toArray(String[]::new);
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/documents/{docId}").setAllowedOrigins(allowedOrigins);
  }
}
