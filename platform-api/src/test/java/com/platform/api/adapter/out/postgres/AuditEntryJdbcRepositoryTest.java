package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEntry.ChangedField;
import com.platform.domain.model.AuditEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEntryJdbcRepositoryTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AuditEntryJdbcRepository repository =
            new AuditEntryJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE audit_entries", Map.of());
    }

    @Test
    void findByTenantAndWorkItemId_returnsEmptyWhenNoneExist() {
        assertThat(repository.findByTenantAndWorkItemId("tenant-1", "wi-1")).isEmpty();
    }

    @Test
    void save_persistsEntryAndCanBeRetrieved() {
        AuditEntry entry = entry("e-1", "tenant-1", "wi-1", AuditEventType.STATE_TRANSITION,
                "UNDER_REVIEW", "CLOSED", List.of(new ChangedField("status", "UNDER_REVIEW", "CLOSED")));

        repository.save(entry);

        List<AuditEntry> results = repository.findByTenantAndWorkItemId("tenant-1", "wi-1");
        assertThat(results).hasSize(1);
        AuditEntry saved = results.get(0);
        assertThat(saved.id()).isEqualTo("e-1");
        assertThat(saved.eventType()).isEqualTo(AuditEventType.STATE_TRANSITION);
        assertThat(saved.previousState()).isEqualTo("UNDER_REVIEW");
        assertThat(saved.newState()).isEqualTo("CLOSED");
        assertThat(saved.changedFields()).hasSize(1);
        assertThat(saved.changedFields().get(0).fieldPath()).isEqualTo("status");
    }

    @Test
    void save_isIdempotentOnDuplicateId() {
        AuditEntry entry = entry("e-dup", "tenant-1", "wi-1", AuditEventType.INGESTION,
                null, "UNDER_REVIEW", List.of());

        repository.save(entry);
        repository.save(entry); // second call must not throw or duplicate

        assertThat(repository.findByTenantAndWorkItemId("tenant-1", "wi-1")).hasSize(1);
    }

    @Test
    void findByTenantAndWorkItemId_returnsEntriesOrderedByTimestampAscending() {
        Instant base = Instant.now().minusSeconds(60);
        repository.save(entry("e-2", "tenant-1", "wi-1", AuditEventType.STATE_TRANSITION,
                "UNDER_REVIEW", "ESCALATED", List.of(), base.plusSeconds(10)));
        repository.save(entry("e-1", "tenant-1", "wi-1", AuditEventType.INGESTION,
                null, "UNDER_REVIEW", List.of(), base));
        repository.save(entry("e-3", "tenant-1", "wi-1", AuditEventType.SLA_WARNING,
                null, null, List.of(), base.plusSeconds(20)));

        List<AuditEntry> results = repository.findByTenantAndWorkItemId("tenant-1", "wi-1");

        assertThat(results).extracting(AuditEntry::id)
                .containsExactly("e-1", "e-2", "e-3");
    }

    @Test
    void findByTenantAndWorkItemId_doesNotCrossTenantBoundary() {
        repository.save(entry("e-1", "tenant-A", "wi-1", AuditEventType.INGESTION,
                null, "UNDER_REVIEW", List.of()));

        assertThat(repository.findByTenantAndWorkItemId("tenant-B", "wi-1")).isEmpty();
    }

    @Test
    void findByTenantAndWorkItemId_doesNotCrossWorkItemBoundary() {
        repository.save(entry("e-1", "tenant-1", "wi-A", AuditEventType.INGESTION,
                null, "UNDER_REVIEW", List.of()));

        assertThat(repository.findByTenantAndWorkItemId("tenant-1", "wi-B")).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static AuditEntry entry(String id, String tenantId, String workItemId,
                                     AuditEventType type, String prev, String next,
                                     List<ChangedField> fields) {
        return entry(id, tenantId, workItemId, type, prev, next, fields, Instant.now());
    }

    private static AuditEntry entry(String id, String tenantId, String workItemId,
                                     AuditEventType type, String prev, String next,
                                     List<ChangedField> fields, Instant timestamp) {
        return new AuditEntry(id, tenantId, workItemId, UUID.randomUUID().toString(),
                type, prev, next, "some-transition", fields,
                "user-1", "ANALYST", timestamp, id + "-idem");
    }
}
