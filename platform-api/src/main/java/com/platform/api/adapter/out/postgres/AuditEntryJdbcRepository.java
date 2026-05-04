package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.audit.domain.ports.out.IAuditEntryRepository;
import com.platform.config.domain.ports.out.ISubmissionAuditRepository;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEntry.ChangedField;
import com.platform.domain.model.AuditEventType;
import com.platform.ingestion.domain.ports.out.IIngestionAuditRepository;
import com.platform.workflow.domain.ports.out.IWorkflowAuditRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class AuditEntryJdbcRepository
        implements IAuditEntryRepository,
                   IWorkflowAuditRepository,
                   IIngestionAuditRepository,
                   ISubmissionAuditRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditEntryJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(AuditEntry entry) {
        String sql = """
                INSERT INTO audit_entries
                  (id, tenant_id, work_item_id, correlation_id, event_type,
                   previous_state, new_state, transition_name, changed_fields,
                   actor_user_id, actor_role, timestamp, idempotency_key)
                VALUES
                  (:id, :tenantId, :workItemId, :correlationId, :eventType,
                   :previousState, :newState, :transitionName, CAST(:changedFields AS jsonb),
                   :actorUserId, :actorRole, :timestamp, :idempotencyKey)
                ON CONFLICT (id) DO NOTHING
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", entry.id())
                .addValue("tenantId", entry.tenantId())
                .addValue("workItemId", entry.workItemId())
                .addValue("correlationId", entry.correlationId())
                .addValue("eventType", entry.eventType().name())
                .addValue("previousState", entry.previousState())
                .addValue("newState", entry.newState())
                .addValue("transitionName", entry.transitionName())
                .addValue("changedFields", toJsonChangedFields(entry.changedFields()))
                .addValue("actorUserId", entry.actorUserId())
                .addValue("actorRole", entry.actorRole())
                .addValue("timestamp", toOdt(entry.timestamp()))
                .addValue("idempotencyKey", entry.idempotencyKey());
        jdbc.update(sql, params);
    }

    @Override
    public List<AuditEntry> findByTenantAndWorkItemId(String tenantId, String workItemId) {
        String sql = """
                SELECT * FROM audit_entries
                WHERE tenant_id = :tenantId AND work_item_id = :workItemId
                ORDER BY timestamp ASC
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("workItemId", workItemId);
        return jdbc.query(sql, params, this::mapRow);
    }

    private AuditEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEntry(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("work_item_id"),
                rs.getString("correlation_id"),
                AuditEventType.valueOf(rs.getString("event_type")),
                rs.getString("previous_state"),
                rs.getString("new_state"),
                rs.getString("transition_name"),
                parseChangedFields(rs.getString("changed_fields")),
                rs.getString("actor_user_id"),
                rs.getString("actor_role"),
                toInstant(rs, "timestamp"),
                rs.getString("idempotency_key")
        );
    }

    private static OffsetDateTime toOdt(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime odt = rs.getObject(column, OffsetDateTime.class);
        return odt != null ? odt.toInstant() : null;
    }

    private String toJsonChangedFields(List<ChangedField> fields) {
        try {
            List<Map<String, Object>> raw = fields.stream()
                    .map(f -> Map.<String, Object>of(
                            "fieldPath", f.fieldPath() != null ? f.fieldPath() : "",
                            "previousValue", f.previousValue() != null ? f.previousValue() : "",
                            "newValue", f.newValue() != null ? f.newValue() : ""))
                    .toList();
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise changedFields to JSONB", e);
        }
    }

    private List<ChangedField> parseChangedFields(String json) {
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream()
                    .map(m -> new ChangedField(
                            (String) m.get("fieldPath"),
                            m.get("previousValue"),
                            m.get("newValue")))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise changedFields from JSONB", e);
        }
    }
}
