package com.example.docs;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
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
            redisBus,
            new MetricsRegistry());
    var session = mock(WebSocketSession.class);
    when(session.getUri()).thenReturn(new URI("ws://localhost:8080/ws/documents/doc-1?token=bad"));
    when(session.isOpen()).thenReturn(true);

    assertThatCode(() -> handler.afterConnectionEstablished(session)).doesNotThrowAnyException();

    verify(session).sendMessage(any(TextMessage.class));
    verify(session).close(CloseStatus.NOT_ACCEPTABLE);
  }

  @Test
  void invalidDocumentIdSendsErrorAndClosesConnection() throws Exception {
    var token = new JwtManager("test-secret", Duration.ofHours(1))
        .sign(new UserClaims("user-1", "ada@example.com"));
    var redisBus = mock(RedisBus.class);
    var handler =
        new DocumentSocketHandler(
            mock(AppRepository.class),
            new JwtManager("test-secret", Duration.ofHours(1)),
            redisBus,
            new MetricsRegistry());
    var session = mock(WebSocketSession.class);
    when(session.getUri()).thenReturn(new URI("ws://localhost:8080/ws/documents/not-a-uuid?token=" + token));
    when(session.isOpen()).thenReturn(true);
    when(session.getAttributes()).thenReturn(new HashMap<>());

    handler.afterConnectionEstablished(session);

    verify(session).sendMessage(any(TextMessage.class));
    verify(session).close(CloseStatus.NOT_ACCEPTABLE);
  }

  @Test
  void oversizedUpdateSendsErrorAndDoesNotPersist() throws Exception {
    var repository = mock(AppRepository.class);
    var handler =
        new DocumentSocketHandler(
            repository,
            new JwtManager("test-secret", Duration.ofHours(1)),
            mock(RedisBus.class),
            new MetricsRegistry());
    var session = mock(WebSocketSession.class);
    var attributes = new HashMap<String, Object>();
    attributes.put("docId", "11111111-1111-4111-8111-111111111111");
    attributes.put("userId", "user-1");
    attributes.put("role", "editor");
    when(session.getAttributes()).thenReturn(attributes);
    when(session.isOpen()).thenReturn(true);
    var update = Base64.getEncoder().encodeToString(new byte[DocumentSocketHandler.MAX_UPDATE_BYTES + 1]);

    handler.handleTextMessage(
        session,
        new TextMessage(
            ("{\"type\":\"sync:update\",\"update\":\"" + update + "\"}")
                .getBytes(StandardCharsets.UTF_8)));

    verify(repository, never()).appendUpdate(any(), any());
    verify(session).sendMessage(any(TextMessage.class));
  }
}
