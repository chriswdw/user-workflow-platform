package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.domain.model.SourceType;
import com.platform.domain.model.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the optimistic lock guard under real concurrent PostgreSQL writes.
 * The sequential version-mismatch scenario is already covered in WorkItemJdbcRepositoryTest.
 * This test uses two threads racing at the same instant — the only way to prove the
 * WHERE version = :version clause works correctly under actual concurrent load.
 *
 * Repeated 5 times to rule out false-passes from lucky scheduling.
 */
class OptimisticLockConcurrencyTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WorkItemJdbcRepository repository =
            new WorkItemJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE work_items", Map.of());
    }

    @RepeatedTest(5)
    void concurrentSaves_exactlyOneSucceedsAndOneThrowsOptimisticLockFailure()
            throws InterruptedException {

        WorkItem item = workItem("wi-race", "tenant-1");
        insert(item);

        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        Runnable saveAttempt = () -> {
            try {
                startGun.await();
                // Both threads hold item at version=1; exactly one UPDATE will match
                repository.save(item.withStatus("RESOLVED"));
                successes.incrementAndGet();
            } catch (OptimisticLockingFailureException ignored) {
                failures.incrementAndGet();
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            } finally {
                doneLatch.countDown();
            }
        };

        Thread t1 = new Thread(saveAttempt, "contender-1");
        Thread t2 = new Thread(saveAttempt, "contender-2");
        t1.start();
        t2.start();

        startGun.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS))
                .as("threads did not finish in time").isTrue();

        assertThat(unexpectedErrors)
                .as("no unexpected exceptions").isEmpty();
        assertThat(successes.get())
                .as("exactly one save succeeds").isEqualTo(1);
        assertThat(failures.get())
                .as("exactly one save is rejected").isEqualTo(1);

        WorkItem fromDb = repository.findById("tenant-1", "wi-race").orElseThrow();
        assertThat(fromDb.status()).isEqualTo("RESOLVED");
        assertThat(fromDb.version()).isEqualTo(2);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static WorkItem workItem(String id, String tenantId) {
        Instant now = Instant.now();
        return new WorkItem(id, tenantId, "SETTLEMENT_EXCEPTION", "corr-" + id, null,
                SourceType.KAFKA, "src", "idem-" + id,
                "OPEN", "ops", false, Map.of(),
                null, null, null, null, null,
                1, "system", now, now);
    }

    private static void insert(WorkItem w) {
        jdbc.update("""
                INSERT INTO work_items
                  (id, tenant_id, workflow_type, correlation_id, config_version_id,
                   source, source_ref, idempotency_key, status, assigned_group,
                   routed_by_default, fields, priority_score, priority_level,
                   priority_last_calculated_at, pending_checker_id, pending_checker_transition,
                   version, maker_user_id, created_at, updated_at)
                VALUES
                  (:id, :tenantId, :workflowType, :correlationId, null,
                   :source, :sourceRef, :idempotencyKey, :status, :assignedGroup,
                   :routedByDefault, '{}', null, null,
                   null, null, null,
                   :version, :makerUserId, :createdAt, :updatedAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", w.id())
                        .addValue("tenantId", w.tenantId())
                        .addValue("workflowType", w.workflowType())
                        .addValue("correlationId", w.correlationId())
                        .addValue("source", w.source().name())
                        .addValue("sourceRef", w.sourceRef())
                        .addValue("idempotencyKey", w.idempotencyKey())
                        .addValue("status", w.status())
                        .addValue("assignedGroup", w.assignedGroup())
                        .addValue("routedByDefault", w.routedByDefault())
                        .addValue("version", w.version())
                        .addValue("makerUserId", w.makerUserId())
                        .addValue("createdAt", OffsetDateTime.ofInstant(w.createdAt(), ZoneOffset.UTC))
                        .addValue("updatedAt", OffsetDateTime.ofInstant(w.updatedAt(), ZoneOffset.UTC)));
    }
}
