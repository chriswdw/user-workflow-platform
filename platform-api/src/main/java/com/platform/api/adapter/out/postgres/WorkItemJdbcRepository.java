package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.api.domain.ports.IFindWorkItemPort;
import com.platform.api.domain.ports.IListWorkItemsPort;
import com.platform.domain.model.SourceType;
import com.platform.domain.model.WorkItem;
import com.platform.workflow.domain.ports.out.IWorkItemRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WorkItemJdbcRepository implements IFindWorkItemPort, IListWorkItemsPort, IWorkItemRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkItemJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<WorkItem> findById(String tenantId, String workItemId) {
        String sql = """
                SELECT * FROM work_items
                WHERE id = :id AND tenant_id = :tenantId
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", workItemId)
                .addValue("tenantId", tenantId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public List<WorkItem> findByTenantAndWorkflowType(String tenantId, String workflowType) {
        String sql = """
                SELECT * FROM work_items
                WHERE tenant_id = :tenantId AND workflow_type = :workflowType
                ORDER BY priority_score DESC NULLS LAST, created_at DESC
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("workflowType", workflowType);
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public WorkItem save(WorkItem w) {
        String sql = """
                UPDATE work_items SET
                  status                      = :status,
                  assigned_group              = :assignedGroup,
                  routed_by_default           = :routedByDefault,
                  fields                      = CAST(:fields AS jsonb),
                  priority_score              = :priorityScore,
                  priority_level              = :priorityLevel,
                  priority_last_calculated_at = :priorityLastCalculatedAt,
                  pending_checker_id          = :pendingCheckerId,
                  pending_checker_transition  = :pendingCheckerTransition,
                  version                     = version + 1,
                  maker_user_id               = :makerUserId,
                  updated_at                  = :updatedAt
                WHERE id = :id AND tenant_id = :tenantId AND version = :version
                """;
        Instant now = Instant.now();
        var params = new MapSqlParameterSource()
                .addValue("id", w.id())
                .addValue("tenantId", w.tenantId())
                .addValue("status", w.status())
                .addValue("assignedGroup", w.assignedGroup())
                .addValue("routedByDefault", w.routedByDefault())
                .addValue("fields", toJson(w.fields()))
                .addValue("priorityScore", w.priorityScore())
                .addValue("priorityLevel", w.priorityLevel())
                .addValue("priorityLastCalculatedAt", w.priorityLastCalculatedAt())
                .addValue("pendingCheckerId", w.pendingCheckerId())
                .addValue("pendingCheckerTransition", w.pendingCheckerTransition())
                .addValue("makerUserId", w.makerUserId())
                .addValue("updatedAt", now)
                .addValue("version", w.version());

        int rows = jdbc.update(sql, params);
        if (rows == 0) {
            throw new OptimisticLockingFailureException(
                    "WorkItem " + w.id() + " was modified concurrently (expected version " + w.version() + ")");
        }
        return new WorkItem(w.id(), w.tenantId(), w.workflowType(), w.correlationId(), w.configVersionId(),
                w.source(), w.sourceRef(), w.idempotencyKey(),
                w.status(), w.assignedGroup(), w.routedByDefault(), w.fields(),
                w.priorityScore(), w.priorityLevel(), w.priorityLastCalculatedAt(),
                w.pendingCheckerId(), w.pendingCheckerTransition(),
                w.version() + 1, w.makerUserId(), w.createdAt(), now);
    }

    private WorkItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        Integer priorityScore = rs.getObject("priority_score") != null ? rs.getInt("priority_score") : null;
        return new WorkItem(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("workflow_type"),
                rs.getString("correlation_id"),
                rs.getString("config_version_id"),
                SourceType.valueOf(rs.getString("source")),
                rs.getString("source_ref"),
                rs.getString("idempotency_key"),
                rs.getString("status"),
                rs.getString("assigned_group"),
                rs.getBoolean("routed_by_default"),
                parseJson(rs.getString("fields")),
                priorityScore,
                rs.getString("priority_level"),
                toInstant(rs, "priority_last_calculated_at"),
                rs.getString("pending_checker_id"),
                rs.getString("pending_checker_transition"),
                rs.getInt("version"),
                rs.getString("maker_user_id"),
                toInstant(rs, "created_at"),
                toInstant(rs, "updated_at")
        );
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime odt = rs.getObject(column, OffsetDateTime.class);
        return odt != null ? odt.toInstant() : null;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise fields to JSONB", e);
        }
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise fields from JSONB", e);
        }
    }
}
