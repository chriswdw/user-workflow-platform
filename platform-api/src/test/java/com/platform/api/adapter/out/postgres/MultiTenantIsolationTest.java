package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.domain.model.ConfigType;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
import com.platform.domain.model.SourceType;
import com.platform.domain.model.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that all three JDBC repositories enforce tenant boundaries correctly.
 * Each test inserts data for tenant-A and asserts that tenant-B sees nothing,
 * even when IDs overlap. This is a regulatory requirement for a multi-tenant
 * financial services platform — tenant data leakage is a serious incident.
 */
class MultiTenantIsolationTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WorkItemJdbcRepository workItems =
            new WorkItemJdbcRepository(jdbc, objectMapper);
    private final AuditEntryJdbcRepository auditEntries =
            new AuditEntryJdbcRepository(jdbc, objectMapper);
    private final ConfigDocumentJdbcRepository configDocs =
            new ConfigDocumentJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE work_items, audit_entries, config_documents", Map.of());
    }

    // ── WorkItem isolation ───────────────────────────────────────────────────

    @Test
    void findById_doesNotReturnWorkItemOwnedByDifferentTenant() {
        insertWorkItem("wi-shared-id", "tenant-A", "SETTLEMENT_EXCEPTION");

        assertThat(workItems.findById("tenant-B", "wi-shared-id")).isEmpty();
    }

    @Test
    void findByTenantAndWorkflowType_doesNotReturnWorkItemsOwnedByDifferentTenant() {
        insertWorkItem("wi-A-1", "tenant-A", "SETTLEMENT_EXCEPTION");
        insertWorkItem("wi-A-2", "tenant-A", "SETTLEMENT_EXCEPTION");
        insertWorkItem("wi-B-1", "tenant-B", "SETTLEMENT_EXCEPTION");

        List<WorkItem> results = workItems.findByTenantAndWorkflowType("tenant-A", "SETTLEMENT_EXCEPTION");

        assertThat(results).hasSize(2)
                .extracting(WorkItem::tenantId)
                .containsOnly("tenant-A");
    }

    @Test
    void idempotencyKeyUniqueness_isScopedPerTenantNotGlobally() {
        // The same idempotency key value must be allowed in different tenants —
        // the UNIQUE constraint is on (tenant_id, workflow_type, idempotency_key).
        insertWorkItemWithIdemKey("wi-A-1", "tenant-A", "SETTLEMENT_EXCEPTION", "shared-idem-key");
        insertWorkItemWithIdemKey("wi-B-1", "tenant-B", "SETTLEMENT_EXCEPTION", "shared-idem-key");

        assertThat(workItems.findById("tenant-A", "wi-A-1")).isPresent();
        assertThat(workItems.findById("tenant-B", "wi-B-1")).isPresent();
    }

    // ── AuditEntry isolation ─────────────────────────────────────────────────

    @Test
    void findAuditEntries_doesNotReturnEntriesOwnedByDifferentTenant() {
        insertWorkItem("wi-1", "tenant-A", "SETTLEMENT_EXCEPTION");
        auditEntries.save(auditEntry("ae-1", "tenant-A", "wi-1"));
        auditEntries.save(auditEntry("ae-2", "tenant-B", "wi-1"));

        List<AuditEntry> forA = auditEntries.findByTenantAndWorkItemId("tenant-A", "wi-1");
        List<AuditEntry> forB = auditEntries.findByTenantAndWorkItemId("tenant-B", "wi-1");

        assertThat(forA).hasSize(1).extracting(AuditEntry::id).containsExactly("ae-1");
        assertThat(forB).hasSize(1).extracting(AuditEntry::id).containsExactly("ae-2");
    }

    // ── ConfigDocument isolation ─────────────────────────────────────────────

    @Test
    void findAllActiveByTenant_doesNotReturnDocumentsOwnedByDifferentTenant() {
        insertConfig("cfg-A", "tenant-A");
        insertConfig("cfg-B", "tenant-B");

        assertThat(configDocs.findAllActiveByTenant("tenant-A"))
                .extracting(d -> d.id()).containsExactly("cfg-A");
        assertThat(configDocs.findAllActiveByTenant("tenant-B"))
                .extracting(d -> d.id()).containsExactly("cfg-B");
    }

    @Test
    void findByTenantAndWorkflowTypeAndType_doesNotReturnDocumentsOwnedByDifferentTenant() {
        insertConfig("cfg-A", "tenant-A");
        insertConfig("cfg-B", "tenant-B");

        assertThat(configDocs.findByTenantAndWorkflowTypeAndType(
                "tenant-A", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG))
                .hasSize(1)
                .extracting(d -> d.id())
                .containsExactly("cfg-A");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void insertWorkItem(String id, String tenantId, String workflowType) {
        insertWorkItemWithIdemKey(id, tenantId, workflowType, tenantId + ":" + id);
    }

    private static void insertWorkItemWithIdemKey(String id, String tenantId,
                                                   String workflowType, String idemKey) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO work_items
                  (id, tenant_id, workflow_type, correlation_id, config_version_id,
                   source, source_ref, idempotency_key, status, assigned_group,
                   routed_by_default, fields, priority_score, priority_level,
                   priority_last_calculated_at, pending_checker_id, pending_checker_transition,
                   version, maker_user_id, created_at, updated_at)
                VALUES
                  (:id, :tenantId, :workflowType, :corrId, null,
                   'KAFKA', :sourceRef, :idemKey, 'OPEN', 'ops',
                   false, '{}', null, null,
                   null, null, null,
                   1, 'system', :now, :now)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("workflowType", workflowType)
                        .addValue("corrId", UUID.randomUUID().toString())
                        .addValue("sourceRef", "src-" + id)
                        .addValue("idemKey", idemKey)
                        .addValue("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)));
    }

    private static AuditEntry auditEntry(String id, String tenantId, String workItemId) {
        return new AuditEntry(id, tenantId, workItemId, UUID.randomUUID().toString(),
                AuditEventType.INGESTION, null, "OPEN", null, List.of(),
                "system", "SYSTEM", Instant.now(), id + "-idem");
    }

    private static void insertConfig(String id, String tenantId) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("key", "val"));
            jdbc.update("""
                    INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
                    VALUES (:id, :tenantId, 'SETTLEMENT_EXCEPTION', 'WORKFLOW_CONFIG', CAST(:content AS jsonb), '1', true)
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("tenantId", tenantId)
                            .addValue("content", json));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
