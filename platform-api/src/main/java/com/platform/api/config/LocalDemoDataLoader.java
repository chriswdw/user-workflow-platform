package com.platform.api.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration
@Profile("local")
public class LocalDemoDataLoader {

    private static final String TENANT_1 = "tenant-1";
    private static final String SETTLEMENT_EX = "SETTLEMENT_EXCEPTION";
    private static final String PARAM_TENANT_ID = "tenantId";
    private static final String WI_001 = "wi-001";
    private static final String WI_003 = "wi-003";
    private static final String STATUS_UNDER_REVIEW = "UNDER_REVIEW";
    private static final String STATUS_ESCALATED = "ESCALATED";
    private static final String SOURCE_KAFKA = "KAFKA";
    private static final String GROUP_OPS = "group-ops";
    private static final String FIELD_TRADE = "trade";
    private static final String FIELD_VALUE_DATE = "valueDate";
    private static final String FIELD_CURRENCY = "currency";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_NOTIONAL_AMOUNT = "notionalAmount";
    private static final String FIELD_COUNTERPARTY = "counterparty";

    @Bean
    public ApplicationRunner loadDemoData(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        return args -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM work_items WHERE tenant_id = :tenantId",
                    Map.of(PARAM_TENANT_ID, TENANT_1),
                    Integer.class);
            if (count != null && count > 0) return;

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            insertWorkItems(jdbc, mapper, now);
            insertAuditEntries(jdbc, now);
        };
    }

    private void insertWorkItems(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, OffsetDateTime now) {
        String sql = """
                INSERT INTO work_items (id, tenant_id, workflow_type, correlation_id, source, source_ref,
                    idempotency_key, status, assigned_group, routed_by_default, fields, priority_score,
                    priority_level, version, maker_user_id, created_at, updated_at)
                VALUES (:id, :tenantId, :workflowType, :correlationId, :source, :sourceRef,
                    :idempotencyKey, :status, :assignedGroup, false, CAST(:fields AS jsonb), :priorityScore,
                    :priorityLevel, 1, 'system', :createdAt, :createdAt)
                ON CONFLICT DO NOTHING
                """;

        for (var item : demoItems(now)) {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", item.id())
                    .addValue(PARAM_TENANT_ID, TENANT_1)
                    .addValue("workflowType", SETTLEMENT_EX)
                    .addValue("correlationId", UUID.randomUUID().toString())
                    .addValue("source", item.source())
                    .addValue("sourceRef", "src-" + item.id())
                    .addValue("idempotencyKey", item.id() + "-idem")
                    .addValue("status", item.status())
                    .addValue("assignedGroup", item.group())
                    .addValue("fields", toJson(mapper, item.fields()))
                    .addValue("priorityScore", item.priorityScore())
                    .addValue("priorityLevel", item.priorityLevel())
                    .addValue("createdAt", item.createdAt()));
        }
    }

    private void insertAuditEntries(NamedParameterJdbcTemplate jdbc, OffsetDateTime now) {
        String sql = """
                INSERT INTO audit_entries (id, tenant_id, work_item_id, correlation_id, event_type,
                    previous_state, new_state, changed_fields, actor_user_id, actor_role, timestamp)
                VALUES (:id, :tenantId, :workItemId, :correlationId, :eventType,
                    :previousState, :newState, '[]'::jsonb, 'system', 'SYSTEM', :timestamp)
                """;

        record AuditRow(String workItemId, String eventType, String previousState,
                        String newState, OffsetDateTime timestamp) {}

        OffsetDateTime base = now.minusSeconds(7200);
        List<AuditRow> rows = List.of(
                new AuditRow(WI_001, "INGESTION",        null,                 STATUS_UNDER_REVIEW, base),
                new AuditRow(WI_001, "ASSIGNMENT",       STATUS_UNDER_REVIEW,  STATUS_UNDER_REVIEW, base.plusSeconds(5)),
                new AuditRow(WI_003, "INGESTION",        null,                 STATUS_UNDER_REVIEW, base.minusSeconds(10800)),
                new AuditRow(WI_003, "STATE_TRANSITION", STATUS_UNDER_REVIEW,  STATUS_ESCALATED,    base.minusSeconds(7200)),
                new AuditRow(WI_003, "SLA_WARNING",      STATUS_ESCALATED,     STATUS_ESCALATED,    base.minusSeconds(3600))
        );

        for (var row : rows) {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue(PARAM_TENANT_ID, TENANT_1)
                    .addValue("workItemId", row.workItemId())
                    .addValue("correlationId", UUID.randomUUID().toString())
                    .addValue("eventType", row.eventType())
                    .addValue("previousState", row.previousState())
                    .addValue("newState", row.newState())
                    .addValue("timestamp", row.timestamp()));
        }
    }

    // ── Seed data ─────────────────────────────────────────────────────────────

    private record DemoItem(String id, String source, String status, String group,
                            int priorityScore, String priorityLevel,
                            OffsetDateTime createdAt, Map<String, Object> fields) {}

    private static List<DemoItem> demoItems(OffsetDateTime now) {
        return List.of(
                new DemoItem(WI_001, SOURCE_KAFKA, STATUS_UNDER_REVIEW, GROUP_OPS, 750, "CRITICAL",
                        now.minusSeconds(7200),
                        Map.of(FIELD_TRADE, Map.of("ref", "TRD-20241015-001", FIELD_VALUE_DATE, "2024-10-17",
                                FIELD_NOTIONAL_AMOUNT, Map.of(FIELD_AMOUNT, "15000000.00", FIELD_CURRENCY, "GBP")),
                            FIELD_COUNTERPARTY, Map.of("name", "Barclays Capital", "lei", "G5GSEF7VJP5I7OUK5573"))),
                new DemoItem("wi-002", SOURCE_KAFKA, STATUS_UNDER_REVIEW, GROUP_OPS, 400, "HIGH",
                        now.minusSeconds(3600),
                        Map.of(FIELD_TRADE, Map.of("ref", "TRD-20241015-002", FIELD_VALUE_DATE, "2024-10-18",
                                FIELD_NOTIONAL_AMOUNT, Map.of(FIELD_AMOUNT, "2500000.00", FIELD_CURRENCY, "EUR")),
                            FIELD_COUNTERPARTY, Map.of("name", "Deutsche Bank AG", "lei", "7LTWFZYICNSX8D621K86"))),
                new DemoItem(WI_003, "DB_POLL", STATUS_ESCALATED, "group-senior", 850, "CRITICAL",
                        now.minusSeconds(18000),
                        Map.of(FIELD_TRADE, Map.of("ref", "TRD-20241014-087", FIELD_VALUE_DATE, "2024-10-15",
                                FIELD_NOTIONAL_AMOUNT, Map.of(FIELD_AMOUNT, "50000000.00", FIELD_CURRENCY, "USD")),
                            FIELD_COUNTERPARTY, Map.of("name", "JP Morgan Chase", "lei", "8I5DZWZKVSZI1NUHU748"))),
                new DemoItem("wi-004", "FILE_UPLOAD", "CLOSED", GROUP_OPS, 50, "LOW",
                        now.minusSeconds(86400),
                        Map.of(FIELD_TRADE, Map.of("ref", "TRD-20241013-044", FIELD_VALUE_DATE, "2024-10-14",
                                FIELD_NOTIONAL_AMOUNT, Map.of(FIELD_AMOUNT, "175000.00", FIELD_CURRENCY, "GBP")),
                            FIELD_COUNTERPARTY, Map.of("name", "HSBC Holdings", "lei", "MLU0ZO3ML4LN2LL2TL39"))),
                new DemoItem("wi-005", SOURCE_KAFKA, STATUS_UNDER_REVIEW, GROUP_OPS, 300, "MEDIUM",
                        now.minusSeconds(1800),
                        Map.of(FIELD_TRADE, Map.of("ref", "TRD-20241015-031", FIELD_VALUE_DATE, "2024-10-20",
                                FIELD_NOTIONAL_AMOUNT, Map.of(FIELD_AMOUNT, "8750000.00", FIELD_CURRENCY, "CHF")),
                            FIELD_COUNTERPARTY, Map.of("name", "UBS Group AG", "lei", "BFM8T61CT2L1QCEMIK50")))
        );
    }

    private static String toJson(ObjectMapper mapper, Map<String, Object> fields) {
        try {
            return mapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise demo fields to JSONB", e);
        }
    }
}
