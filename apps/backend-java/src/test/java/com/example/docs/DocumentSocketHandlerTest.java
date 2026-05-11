package com.example.docs;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class DocumentSocketHandlerTest {
  @Test
  void invalidTokenSendsErrorAndClosesConnection() throws Exception {
    var redisBus = mock(RedisBus.class);
    var handler =
        new DocumentSocketHandler(
            mock(AppRepository.class),
            new JwtManager("test-secret", Duration.ofHours(1)),
            redisBus);
    var session = mock(WebSocketSession.class);
    when(session.getUri()).thenReturn(new URI("ws://localhost:8080/ws/documents/doc-1?token=bad"));
    when(session.isOpen()).thenReturn(true);

    assertThatCode(() -> handler.afterConnectionEstablished(session)).doesNotThrowAnyException();

    verify(session).sendMessage(any(TextMessage.class));
    verify(session).close(CloseStatus.NOT_ACCEPTABLE);
  }
}
