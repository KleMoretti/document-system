package com.example.docs;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

  @Test
  void snapshotMessagePersistsSnapshotForEditors() throws Exception {
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
    when(repository.loadDocumentState("11111111-1111-4111-8111-111111111111"))
        .thenReturn(new DocumentState(null, 0, Collections.nCopies(100, "update".getBytes(StandardCharsets.UTF_8))));
    var snapshot = Base64.getEncoder().encodeToString("state".getBytes(StandardCharsets.UTF_8));

    handler.handleTextMessage(
        session,
        new TextMessage(
            ("{\"type\":\"sync:snapshot\",\"snapshot\":\"" + snapshot + "\",\"snapshotSeq\":100}")
                .getBytes(StandardCharsets.UTF_8)));

    verify(repository).saveSnapshot(eq("11111111-1111-4111-8111-111111111111"), eq(100L), any());
  }

  @Test
  void outboundClientClosesSlowSessionWhenQueueIsFull() throws Exception {
    var session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    var metrics = new MetricsRegistry();
    var client = new OutboundWebSocketClient(session, 1, metrics);

    client.enqueue("{\"type\":\"presence:update\"}");
    var accepted = client.enqueue("{\"type\":\"sync:update\"}");

    assertThatCode(() -> {}).doesNotThrowAnyException();
    org.assertj.core.api.Assertions.assertThat(accepted).isFalse();
    verify(session, timeout(1000)).sendMessage(any(TextMessage.class));
    verify(session, timeout(1000)).close(CloseStatus.SESSION_NOT_RELIABLE);
    org.assertj.core.api.Assertions.assertThat(metrics.render())
        .contains("documentation_collab_ws_errors_total{code=\"SLOW_CLIENT\"} 1")
        .contains("documentation_collab_ws_slow_clients_total 1");
  }

  @Test
  void outboundClientSerializesSlowClientErrorWithActiveWriter() throws Exception {
    var session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    var firstSendEntered = new CountDownLatch(1);
    var secondSendEntered = new CountDownLatch(1);
    var releaseFirstSend = new AtomicBoolean(false);
    var inFlightSends = new AtomicInteger();
    var maxConcurrentSends = new AtomicInteger();
    var sendCalls = new AtomicInteger();
    doAnswer(
            invocation -> {
              var active = inFlightSends.incrementAndGet();
              maxConcurrentSends.updateAndGet(current -> Math.max(current, active));
              if (sendCalls.incrementAndGet() == 1) {
                firstSendEntered.countDown();
                while (!releaseFirstSend.get()) {
                  Thread.onSpinWait();
                }
              } else {
                secondSendEntered.countDown();
              }
              inFlightSends.decrementAndGet();
              return null;
            })
        .when(session)
        .sendMessage(any(TextMessage.class));
    var client = new OutboundWebSocketClient(session, 1, new MetricsRegistry());

    client.start();
    client.enqueue("{\"type\":\"presence:update\"}");
    org.assertj.core.api.Assertions.assertThat(firstSendEntered.await(1, TimeUnit.SECONDS)).isTrue();
    client.enqueue("{\"type\":\"presence:update\"}");
    client.enqueue("{\"type\":\"presence:update\"}");

    org.assertj.core.api.Assertions.assertThat(secondSendEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
    org.assertj.core.api.Assertions.assertThat(maxConcurrentSends.get()).isEqualTo(1);

    releaseFirstSend.set(true);
    verify(session, timeout(1000).atLeast(2)).sendMessage(any(TextMessage.class));
    client.stop();
  }
}
