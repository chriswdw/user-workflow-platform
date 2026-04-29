package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.domain.model.WorkItem;
import com.platform.ingestion.domain.exception.DuplicateIdempotencyKeyException;
import com.platform.ingestion.domain.ports.out.IIngestionWorkItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class IngestionWorkItemJdbcRepository implements IIngestionWorkItemRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IngestionWorkItemJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkItem save(WorkItem w) {
        String sql = """
                INSERT INTO work_items
                  (id, tenant_id, workflow_type, correlation_id, config_version_id,
                   source, source_ref, idempotency_key, status, assigned_group,
                   routed_by_default, fields, priority_score, priority_level,
                   priority_last_calculated_at, pending_checker_id,
                   pending_checker_transition, version, maker_user_id,
                   created_at, updated_at)
                VALUES
                  (:id, :tenantId, :workflowType, :correlationId, :configVersionId,
                   :source, :sourceRef, :idempotencyKey, :status, :assignedGroup,
                   :routedByDefault, CAST(:fields AS jsonb), :priorityScore, :priorityLevel,
                   :priorityLastCalculatedAt, :pendingCheckerId,
                   :pendingCheckerTransition, :version, :makerUserId,
                   :createdAt, :updatedAt)
                """;
        var params = new MapSqlParameterSource()
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
                .addValue("updatedAt", toOdt(w.updatedAt()));
        try {
            jdbc.update(sql, params);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateIdempotencyKeyException(w.idempotencyKey());
        }
        return w;
    }

    private static OffsetDateTime toOdt(java.time.Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private String toJson(java.util.Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise fields to JSONB", e);
        }
    }
}
