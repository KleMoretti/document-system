package com.example.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class UpdateBatcherTest {
  @Test
  void flushesMultipleUpdatesTogetherWhenMaxBatchSizeIsReached() throws Exception {
    var repository = mock(AppRepository.class);
    var batcher = new UpdateBatcher(repository, new MetricsRegistry(), Duration.ofHours(1), 2);

    var first =
        CompletableFuture.runAsync(
            () -> batcher.append("11111111-1111-4111-8111-111111111111", "a".getBytes(StandardCharsets.UTF_8)).join());
    var second =
        CompletableFuture.runAsync(
            () -> batcher.append("11111111-1111-4111-8111-111111111111", "b".getBytes(StandardCharsets.UTF_8)).join());

    CompletableFuture.allOf(first, second).get(1, TimeUnit.SECONDS);

    verify(repository)
        .appendUpdates(
            eq("11111111-1111-4111-8111-111111111111"),
            argThat(
                updates ->
                    updates.size() == 2
                        && new String(updates.get(0), StandardCharsets.UTF_8).equals("a")
                        && new String(updates.get(1), StandardCharsets.UTF_8).equals("b")));
  }

  @Test
  void acceptsOnlyLatestUsefulSnapshots() {
    assertThat(UpdateBatcher.snapshotAllowed(120, 20, 100, 100)).isTrue();
    assertThat(UpdateBatcher.snapshotAllowed(119, 20, 100, 100)).isFalse();
    assertThat(UpdateBatcher.snapshotAllowed(60, 20, 40, 100)).isFalse();
  }

  @Test
  void canBeCreatedAsSpringBeanWithRepositoryAndMetricsDependencies() {
    var context = new AnnotationConfigApplicationContext();
    context.registerBean(AppRepository.class, () -> mock(AppRepository.class));
    context.registerBean(MetricsRegistry.class);
    context.registerBean(UpdateBatcher.class);

    context.refresh();

    assertThat(context.getBean(UpdateBatcher.class)).isNotNull();
    context.close();
  }
}
