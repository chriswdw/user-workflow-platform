package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.domain.model.SourceType;
import com.platform.domain.model.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkItemJdbcRepositoryTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WorkItemJdbcRepository repository =
            new WorkItemJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE work_items CASCADE", Map.of());
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        Optional<WorkItem> result = repository.findById("tenant-1", "nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void findById_returnsWorkItemWhenFound() {
        WorkItem item = workItem("wi-1", "tenant-1", "SETTLEMENT_EXCEPTION", "UNDER_REVIEW", 500);
        insert(item);

        Optional<WorkItem> result = repository.findById("tenant-1", "wi-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("wi-1");
        assertThat(result.get().status()).isEqualTo("UNDER_REVIEW");
        assertThat(result.get().tenantId()).isEqualTo("tenant-1");
        assertThat(result.get().fields()).containsKey("tradeRef");
    }

    @Test
    void findById_doesNotCrossTenanBoundary() {
        insert(workItem("wi-1", "tenant-A", "SETTLEMENT_EXCEPTION", "UNDER_REVIEW", 100));

        assertThat(repository.findById("tenant-B", "wi-1")).isEmpty();
    }

    @Test
    void findByTenantAndWorkflowType_returnsEmptyListWhenNoneMatch() {
        insert(workItem("wi-1", "tenant-1", "SETTLEMENT_EXCEPTION", "UNDER_REVIEW", 100));

        List<WorkItem> result = repository.findByTenantAndWorkflowType("tenant-1", "OTHER_TYPE");

        assertThat(result).isEmpty();
    }

    @Test
    void findByTenantAndWorkflowType_returnsMatchingItemsOrderedByPriorityDescending() {
        insert(workItem("wi-low",  "tenant-1", "SETTLEMENT_EXCEPTION", "UNDER_REVIEW", 100));
        insert(workItem("wi-high", "tenant-1", "SETTLEMENT_EXCEPTION", "ESCALATED",    900));
        insert(workItem("wi-mid",  "tenant-1", "SETTLEMENT_EXCEPTION", "UNDER_REVIEW", 500));

        List<WorkItem> result = repository.findByTenantAndWorkflowType("tenant-1", "SETTLEMENT_EXCEPTION");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo("wi-high");
        assertThat(result.get(1).id()).isEqualTo("wi-mid");
        assertThat(result.get(2).id()).isEqualTo("wi-low");
    }

    @Test
    void findByTenantAndWorkflowType_doesNotCrossTenantBoundary() {
        insert(workItem("wi-1", "tenant-A", "SETTLEMENT_EXCEPTION", "UNDER_REVIEW", 100));
        insert(workItem("wi-2", "tenant-B", "SETTLEMENT_EXCEPTION", "UNDER_REVIEW", 100));

        assertThat(repository.findByTenantAndWorkflowType("tenant-A", "SETTLEMENT_EXCEPTION"))
                .hasSize(1)
                .extracting(WorkItem::id)
                .containsExactly("wi-1");
    }

    @Test
    void save_updatesWorkItemAndIncrementsVersion() {
        WorkItem item = workItem("wi-1", "tenant-1", "SETTLEMENT_EXCEPTION", "UNDER_REVIEW", 500);
        insert(item);

        WorkItem updated = item.withStatus("CLOSED");
        WorkItem saved = repository.save(updated);

        assertThat(saved.status()).isEqualTo("CLOSED");
        assertThat(saved.version()).isEqualTo(2);

        WorkItem fromDb = repository.findById("tenant-1", "wi-1").orElseThrow();
        assertThat(fromDb.status()).isEqualTo("CLOSED");
        assertThat(fromDb.version()).isEqualTo(2);
    }

    @Test
    void save_throwsOptimisticLockingFailureOnVersionMismatch() {
        WorkItem item = workItem("wi-1", "tenant-1", "SETTLEMENT_EXCEPTION", "UNDER_REVIEW", 500);
        insert(item);

        // Simulate concurrent update: version in DB is now 1, we provide stale version 0
        WorkItem staleVersion = new WorkItem(
                item.id(), item.tenantId(), item.workflowType(), item.correlationId(),
                item.configVersionId(), item.source(), item.sourceRef(), item.idempotencyKey(),
                "CLOSED", item.assignedGroup(), item.routedByDefault(), item.fields(),
                item.priorityScore(), item.priorityLevel(), item.priorityLastCalculatedAt(),
                item.pendingCheckerId(), item.pendingCheckerTransition(),
                0, item.makerUserId(), item.createdAt(), Instant.now());

        assertThatThrownBy(() -> repository.save(staleVersion))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("wi-1");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static WorkItem workItem(String id, String tenantId, String workflowType,
                                      String status, int priorityScore) {
        Instant now = Instant.now();
        return new WorkItem(id, tenantId, workflowType, "corr-" + id, null,
                SourceType.KAFKA, "src-" + id, "idem-" + id,
                status, "group-ops", false, Map.of("tradeRef", "TRD-001"),
                priorityScore, "HIGH", null, null, null,
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
                  (:id, :tenantId, :workflowType, :correlationId, :configVersionId,
                   :source, :sourceRef, :idempotencyKey, :status, :assignedGroup,
                   :routedByDefault, CAST(:fields AS jsonb), :priorityScore, :priorityLevel,
                   :priorityLastCalculatedAt, :pendingCheckerId, :pendingCheckerTransition,
                   :version, :makerUserId, :createdAt, :updatedAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", w.id())
                        .addValue("tenantId", w.tenantId())
                        .addValue("workflowType", w.workflowType())
                        .addValue("correlationId", w.correlationId())
                        .addValue("configVersionId", w.configVersionId())
                        .addValue("source", w.source().name())
                        .addValue("sourceRef", w.sourceRef())
                        .addValue("idempotencyKey", w.idempotencyKey())
                        .addValue("status", w.status())
                        .addValue("assignedGroup", w.assignedGroup())
                        .addValue("routedByDefault", w.routedByDefault())
                        .addValue("fields", toJson(w.fields()))
                        .addValue("priorityScore", w.priorityScore())
                        .addValue("priorityLevel", w.priorityLevel())
                        .addValue("priorityLastCalculatedAt", toOdt(w.priorityLastCalculatedAt()))
                        .addValue("pendingCheckerId", w.pendingCheckerId())
                        .addValue("pendingCheckerTransition", w.pendingCheckerTransition())
                        .addValue("version", w.version())
                        .addValue("makerUserId", w.makerUserId())
                        .addValue("createdAt", toOdt(w.createdAt()))
                        .addValue("updatedAt", toOdt(w.updatedAt())));
    }

    private static OffsetDateTime toOdt(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
