package com.platform.api.adapter.out.postgres;

import com.platform.domain.model.WorkItem;
import com.platform.ingestion.domain.exception.DuplicateIdempotencyKeyException;
import com.platform.ingestion.domain.ports.out.IIngestionWorkItemRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class IngestionWorkItemJdbcRepository implements IIngestionWorkItemRepository {

  private final NamedParameterJdbcTemplate jdbc;
  private static final String IDEMPOTENCY_KEY_INDEX = "idx_work_items_idempotency";

  private final ObjectMapper objectMapper;

  public IngestionWorkItemJdbcRepository(
      NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public WorkItem save(WorkItem w) {
    String sql =
        """
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
    var params =
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
            .addValue("updatedAt", toOdt(w.updatedAt()));
    try {
      jdbc.update(sql, params);
    } catch (DuplicateKeyException e) {
      // Only a collision on the idempotency-key index means "this record was already ingested".
      // Any other unique violation (for example the primary key) or integrity failure is a real
      // fault and must surface unchanged, not be reported as a duplicate delivery.
      if (isIdempotencyKeyViolation(e)) {
        throw new DuplicateIdempotencyKeyException(w.idempotencyKey());
      }
      throw e;
    }
    return w;
  }

  /**
   * True when PostgreSQL reports that the unique index on (tenant, workflow type, idempotency key)
   * was the one violated. If the violated constraint cannot be determined the answer is false: an
   * unknown unique violation is surfaced, never assumed to be a duplicate.
   */
  private static boolean isIdempotencyKeyViolation(DuplicateKeyException e) {
    for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
      if (cause instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
        return IDEMPOTENCY_KEY_INDEX.equals(psql.getServerErrorMessage().getConstraint());
      }
    }
    return false;
  }

  private static OffsetDateTime toOdt(java.time.Instant instant) {
    return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
  }

  private String toJson(java.util.Map<String, Object> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialise fields to JSONB", e);
    }
  }
}
