package com.platform.api.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.domain.model.ConnectionConfig;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Two admins editing the same source connection at the same instant: exactly one write may win.
 * Sequential stale-version behaviour is covered in SourceConnectionJdbcRepositoryTest; only two
 * threads racing prove the {@code WHERE version = :version} guard under real concurrent writes.
 * Repeated to rule out lucky scheduling, like OptimisticLockConcurrencyTest does for work items.
 */
class SourceConnectionOptimisticLockConcurrencyTest {

  private static final NamedParameterJdbcTemplate jdbc =
      new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);

  private final SourceConnectionJdbcRepository repository =
      new SourceConnectionJdbcRepository(jdbc, new JsonMapper());

  @BeforeEach
  void truncate() {
    jdbc.update("TRUNCATE source_connections CASCADE", Map.of());
  }

  @RepeatedTest(5)
  void concurrentUpdates_exactlyOneSucceedsAndOneIsRejected() throws InterruptedException {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    SourceConnection original =
        repository.insert(
            new SourceConnection(
                "conn-race",
                "kafka-race",
                "Original",
                ConnectionType.KAFKA,
                new ConnectionConfig.KafkaConfig("broker:9092", "trades"),
                null,
                1,
                "admin",
                now,
                now));

    CountDownLatch startGun = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();
    List<Throwable> unexpected = new CopyOnWriteArrayList<>();

    for (String editor : List.of("editor-a", "editor-b")) {
      new Thread(
              () -> {
                try {
                  startGun.await();
                  // Both threads hold the same version-1 read; only one UPDATE can match it.
                  repository.update(withDisplayName(original, editor));
                  successes.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                  conflicts.incrementAndGet();
                } catch (Throwable t) {
                  unexpected.add(t);
                } finally {
                  done.countDown();
                }
              },
              editor)
          .start();
    }
    startGun.countDown();

    assertThat(done.await(10, TimeUnit.SECONDS)).as("threads did not finish in time").isTrue();
    assertThat(unexpected).as("no unexpected exceptions").isEmpty();
    assertThat(successes.get()).as("exactly one update wins").isEqualTo(1);
    assertThat(conflicts.get()).as("exactly one update is rejected").isEqualTo(1);
    assertThat(repository.findById("conn-race").orElseThrow().version()).isEqualTo(2);
  }

  private static SourceConnection withDisplayName(SourceConnection c, String displayName) {
    return new SourceConnection(
        c.id(),
        c.name(),
        displayName,
        c.connectionType(),
        c.config(),
        c.credentialsRef(),
        c.version(),
        c.createdBy(),
        c.createdAt(),
        c.updatedAt());
  }
}
