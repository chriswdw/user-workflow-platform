package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.domain.model.SourceType;
import com.platform.domain.model.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyJdbcRepositoryTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final IdempotencyKeyJdbcRepository repository =
            new IdempotencyKeyJdbcRepository(jdbc);
    private final IngestionWorkItemJdbcRepository workItemRepository =
            new IngestionWorkItemJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE work_items CASCADE", Map.of());
    }

    @Test
    void exists_returnsFalseWhenNoWorkItemWithKey() {
        assertThat(repository.exists("tenant-1", "SETTLEMENT_EXCEPTION", "TRD-MISSING")).isFalse();
    }

    @Test
    void exists_returnsTrueAfterWorkItemWithKeyIsSaved() {
        workItemRepository.save(workItem("wi-idem-1", "tenant-1", "SETTLEMENT_EXCEPTION", "KEY-001"));

        assertThat(repository.exists("tenant-1", "SETTLEMENT_EXCEPTION", "KEY-001")).isTrue();
    }

    @Test
    void exists_doesNotCrossTenantBoundary() {
        workItemRepository.save(workItem("wi-idem-2", "tenant-A", "SETTLEMENT_EXCEPTION", "KEY-002"));

        assertThat(repository.exists("tenant-B", "SETTLEMENT_EXCEPTION", "KEY-002")).isFalse();
    }

    @Test
    void exists_doesNotCrossWorkflowTypeBoundary() {
        workItemRepository.save(workItem("wi-idem-3", "tenant-1", "SETTLEMENT_EXCEPTION", "KEY-003"));

        assertThat(repository.exists("tenant-1", "OTHER_WORKFLOW", "KEY-003")).isFalse();
    }

    @Test
    void save_isNoOp_keyIsTrackedViaWorkItemRow() {
        // save() is intentionally a no-op; idempotency key lives in the work_items row
        repository.save("tenant-1", "SETTLEMENT_EXCEPTION", "KEY-NOOP");

        // Key is not visible until a work item with that key is persisted
        assertThat(repository.exists("tenant-1", "SETTLEMENT_EXCEPTION", "KEY-NOOP")).isFalse();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static WorkItem workItem(String id, String tenantId, String workflowType, String key) {
        Instant now = Instant.now();
        return new WorkItem(id, tenantId, workflowType, "corr-" + id, null,
                SourceType.KAFKA, "src-" + id, key,
                "UNDER_REVIEW", "group-ops", false, Map.of(),
                null, null, null, null, null,
                1, "system", now, now);
    }
}
