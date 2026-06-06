package com.example.docs;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class UpdateBatcher {
  private final AppRepository repository;
  private final MetricsRegistry metrics;
  private final Duration flushAge;
  private final int maxSize;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final Map<String, DocumentQueue> queues = new HashMap<>();

  @Autowired
  UpdateBatcher(
      AppRepository repository,
      MetricsRegistry metrics,
      @Value("${app.ws-batch-flush-ms:25}") long flushMs,
      @Value("${app.ws-batch-max-size:32}") int maxSize) {
    this(repository, metrics, Duration.ofMillis(flushMs), maxSize);
  }

  UpdateBatcher(AppRepository repository, MetricsRegistry metrics, Duration flushAge, int maxSize) {
    this.repository = repository;
    this.metrics = metrics;
    this.flushAge = flushAge.isNegative() || flushAge.isZero() ? Duration.ofMillis(25) : flushAge;
    this.maxSize = Math.max(1, maxSize);
  }

  CompletableFuture<Void> append(String docId, byte[] update) {
    var future = new CompletableFuture<Void>();
    boolean flushNow = false;
    synchronized (this) {
      var queue = queues.computeIfAbsent(docId, ignored -> new DocumentQueue());
      queue.items.add(new PendingUpdate(update.clone(), future));
      metrics.observeWebSocketQueueDepth(queue.items.size());
      if (queue.items.size() >= maxSize) {
        if (queue.scheduled != null) {
          queue.scheduled.cancel(false);
          queue.scheduled = null;
        }
        flushNow = true;
      } else if (queue.scheduled == null) {
        queue.scheduled =
            scheduler.schedule(() -> flush(docId), flushAge.toMillis(), TimeUnit.MILLISECONDS);
      }
    }
    if (flushNow) {
      Thread.ofVirtual().start(() -> flush(docId));
    }
    return future;
  }

  static boolean snapshotAllowed(long requestedSeq, long currentSnapshotSeq, int updatesCount, int minUpdates) {
    var safeMinUpdates = Math.max(1, minUpdates);
    return updatesCount >= safeMinUpdates && requestedSeq == currentSnapshotSeq + updatesCount;
  }

  private void flush(String docId) {
    List<PendingUpdate> items;
    synchronized (this) {
      var queue = queues.remove(docId);
      if (queue == null || queue.items.isEmpty()) {
        return;
      }
      if (queue.scheduled != null) {
        queue.scheduled.cancel(false);
      }
      items = new ArrayList<>(queue.items);
    }

    var updates = items.stream().map(PendingUpdate::update).toList();
    var startedAt = System.nanoTime();
    try {
      repository.appendUpdates(docId, updates);
      items.forEach(item -> item.future().complete(null));
    } catch (RuntimeException ex) {
      items.forEach(item -> item.future().completeExceptionally(ex));
    } finally {
      metrics.observeWebSocketPersist(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
      metrics.observeWebSocketBatch(updates.size());
    }
  }

  private static final class DocumentQueue {
    private final List<PendingUpdate> items = new ArrayList<>();
    private ScheduledFuture<?> scheduled;
  }

  private record PendingUpdate(byte[] update, CompletableFuture<Void> future) {}
}
