package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.out.IWorkflowTypeSubmissionRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public class WorkflowTypeSubmissionJdbcRepository implements IWorkflowTypeSubmissionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkflowTypeSubmissionJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkflowTypeSubmission save(WorkflowTypeSubmission s) {
        String sql = """
                INSERT INTO workflow_type_submissions
                    (id, tenant_id, workflow_type, display_name, description,
                     status, draft_configs, submitted_by, submitted_at,
                     reviewed_by, reviewed_at, rejection_reason,
                     current_step, version, created_at, updated_at)
                VALUES (:id, :tenantId, :workflowType, :displayName, :description,
                        :status, CAST(:draftConfigs AS jsonb), :submittedBy, :submittedAt,
                        :reviewedBy, :reviewedAt, :rejectionReason,
                        :currentStep, :version, :createdAt, :updatedAt)
                ON CONFLICT (id) DO UPDATE SET
                    status           = EXCLUDED.status,
                    draft_configs    = EXCLUDED.draft_configs,
                    submitted_at     = EXCLUDED.submitted_at,
                    reviewed_by      = EXCLUDED.reviewed_by,
                    reviewed_at      = EXCLUDED.reviewed_at,
                    rejection_reason = EXCLUDED.rejection_reason,
                    current_step     = EXCLUDED.current_step,
                    version          = EXCLUDED.version,
                    updated_at       = EXCLUDED.updated_at
                WHERE workflow_type_submissions.version = EXCLUDED.version - 1
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", s.id())
                .addValue("tenantId", s.tenantId())
                .addValue("workflowType", s.workflowType())
                .addValue("displayName", s.displayName())
                .addValue("description", s.description())
                .addValue("status", s.status().name())
                .addValue("draftConfigs", toJson(s.draftConfigs()))
                .addValue("submittedBy", s.submittedBy())
                .addValue("submittedAt", s.submittedAt())
                .addValue("reviewedBy", s.reviewedBy())
                .addValue("reviewedAt", s.reviewedAt())
                .addValue("rejectionReason", s.rejectionReason())
                .addValue("currentStep", s.currentStep())
                .addValue("version", s.version())
                .addValue("createdAt", s.createdAt())
                .addValue("updatedAt", s.updatedAt());

        int rows = jdbc.update(sql, params);
        if (rows == 0) {
            throw new OptimisticLockingFailureException(
                    "WorkflowTypeSubmission " + s.id() + " was modified concurrently (expected version "
                    + (s.version() - 1) + ")");
        }
        return s;
    }

    @Override
    public Optional<WorkflowTypeSubmission> findById(String tenantId, String submissionId) {
        String sql = """
                SELECT s.*, ss.display_name AS status_display_name
                FROM workflow_type_submissions s
                JOIN submission_statuses ss ON ss.code = s.status
                WHERE s.tenant_id = :tenantId AND s.id = :id
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", submissionId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public List<WorkflowTypeSubmission> findByTenantAndStatus(String tenantId, SubmissionStatus status) {
        String sql = """
                SELECT s.*, ss.display_name AS status_display_name
                FROM workflow_type_submissions s
                JOIN submission_statuses ss ON ss.code = s.status
                WHERE s.tenant_id = :tenantId AND s.status = :status
                ORDER BY s.updated_at DESC
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("status", status.name());
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public List<WorkflowTypeSubmission> findByTenantAndStatusAndUser(String tenantId,
                                                                      SubmissionStatus status,
                                                                      String userId) {
        String sql = """
                SELECT s.*, ss.display_name AS status_display_name
                FROM workflow_type_submissions s
                JOIN submission_statuses ss ON ss.code = s.status
                WHERE s.tenant_id = :tenantId AND s.submitted_by = :userId AND s.status = :status
                ORDER BY s.updated_at DESC
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("status", status.name())
                .addValue("userId", userId);
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public boolean existsByTenantAndWorkflowType(String tenantId, String workflowType) {
        String sql = """
                SELECT COUNT(*) > 0
                FROM workflow_type_submissions
                WHERE tenant_id = :tenantId AND workflow_type = :workflowType
                  AND status NOT IN ('REJECTED')
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("workflowType", workflowType);
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, params, Boolean.class));
    }

    private WorkflowTypeSubmission mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowTypeSubmission(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("workflow_type"),
                rs.getString("display_name"),
                rs.getString("description"),
                SubmissionStatus.valueOf(rs.getString("status")),
                rs.getString("status_display_name"),
                parseDraftConfigs(rs.getString("draft_configs")),
                rs.getString("submitted_by"),
                rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getString("reviewed_by"),
                rs.getObject("reviewed_at", OffsetDateTime.class),
                rs.getString("rejection_reason"),
                rs.getInt("current_step"),
                rs.getInt("version"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise draft_configs to JSONB", e);
        }
    }

    private DraftConfigs parseDraftConfigs(String json) {
        // DraftConfigs.isComplete() is serialised as "complete" by Jackson; ignore it on read-back.
        try {
            return objectMapper.readerFor(DraftConfigs.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise draft_configs from JSONB", e);
        }
    }

}
