package com.platform.api.adapter.out.postgres;

import com.platform.ingestion.domain.ports.out.IIdempotencyKeyRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Checks idempotency against the work_items table's existing unique index on
 * (tenant_id, workflow_type, idempotency_key). No separate table is needed.
 * save() is a no-op: the key is persisted as part of the WorkItem INSERT.
 */
public class IdempotencyKeyJdbcRepository implements IIdempotencyKeyRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public IdempotencyKeyJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(String tenantId, String workflowType, String idempotencyKey) {
        String sql = """
                SELECT COUNT(*) FROM work_items
                WHERE tenant_id = :tenantId
                  AND workflow_type = :workflowType
                  AND idempotency_key = :idempotencyKey
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("workflowType", workflowType)
                .addValue("idempotencyKey", idempotencyKey);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void save(String tenantId, String workflowType, String idempotencyKey) {
        // no-op: key is persisted as the idempotency_key column of the work item row
    }
}
