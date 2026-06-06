package com.example.docs;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class OutboundWebSocketClient {
  private final WebSocketSession session;
  private final BlockingQueue<TextMessage> queue;
  private final MetricsRegistry metrics;
  private final Object writeLock = new Object();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private Thread writer;

  OutboundWebSocketClient(WebSocketSession session, int queueSize, MetricsRegistry metrics) {
    this.session = session;
    this.queue = new ArrayBlockingQueue<>(Math.max(1, queueSize));
    this.metrics = metrics;
  }

  WebSocketSession session() {
    return session;
  }

  void start() {
    writer = Thread.ofVirtual().start(this::drain);
  }

  boolean enqueue(String text) {
    if (closed.get()) {
      return false;
    }
    var accepted = queue.offer(new TextMessage(text));
    if (!accepted) {
      closeSlowClient();
      return false;
    }
    metrics.observeWebSocketQueueDepth(queue.size());
    return true;
  }

  void stop() {
    if (closed.compareAndSet(false, true) && writer != null) {
      writer.interrupt();
    }
  }

  private void drain() {
    while (!closed.get() && session.isOpen()) {
      try {
        send(queue.take());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception ex) {
        stop();
        return;
      }
    }
  }

  private void closeSlowClient() {
    metrics.observeWebSocketError("SLOW_CLIENT");
    metrics.observeWebSocketSlowClient();
    stop();
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                if (session.isOpen()) {
                  send(
                      new TextMessage(
                          "{\"type\":\"error\",\"code\":\"SLOW_CLIENT\",\"message\":\"Connection closed because the client is not keeping up.\"}"));
                  session.close(CloseStatus.SESSION_NOT_RELIABLE);
                }
              } catch (Exception ignored) {
                // The connection is already unhealthy; close callbacks will finish cleanup.
              }
            });
  }

  private void send(TextMessage message) throws Exception {
    synchronized (writeLock) {
      if (session.isOpen()) {
        session.sendMessage(message);
      }
    }
  }
}
