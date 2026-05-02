package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.domain.ports.out.ISourceConnectionRepository;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import com.platform.domain.model.SourceConnectionAccess;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SourceConnectionJdbcRepository implements ISourceConnectionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SourceConnectionJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceConnection save(SourceConnection c) {
        String sql = """
                INSERT INTO source_connections
                    (id, name, display_name, connection_type, config, credentials_ref,
                     created_by, created_at, updated_at)
                VALUES (:id, :name, :displayName, :connectionType, CAST(:config AS jsonb),
                        :credentialsRef, :createdBy, :createdAt, :updatedAt)
                ON CONFLICT (id) DO UPDATE SET
                    display_name    = EXCLUDED.display_name,
                    config          = EXCLUDED.config,
                    credentials_ref = EXCLUDED.credentials_ref,
                    updated_at      = EXCLUDED.updated_at
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", c.id())
                .addValue("name", c.name())
                .addValue("displayName", c.displayName())
                .addValue("connectionType", c.connectionType().name())
                .addValue("config", toJson(c.config()))
                .addValue("credentialsRef", c.credentialsRef())
                .addValue("createdBy", c.createdBy())
                .addValue("createdAt", c.createdAt())
                .addValue("updatedAt", c.updatedAt()));
        return c;
    }

    @Override
    public Optional<SourceConnection> findById(String id) {
        String sql = "SELECT * FROM source_connections WHERE id = :id";
        return jdbc.query(sql, new MapSqlParameterSource("id", id), this::mapRow)
                .stream().findFirst();
    }

    @Override
    public List<SourceConnection> findAll() {
        return jdbc.query(
                "SELECT * FROM source_connections ORDER BY display_name",
                Map.of(),
                this::mapRow);
    }

    @Override
    public List<SourceConnection> findAccessibleByTenantAndType(String tenantId, ConnectionType type) {
        String sql = """
                SELECT sc.*
                FROM source_connections sc
                JOIN source_connection_access sca ON sca.source_connection_id = sc.id
                WHERE sca.tenant_id = :tenantId AND sc.connection_type = :connectionType
                ORDER BY sc.display_name
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("connectionType", type.name());
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public void grantAccess(SourceConnectionAccess access) {
        String sql = """
                INSERT INTO source_connection_access
                    (id, source_connection_id, tenant_id, granted_by, granted_at)
                VALUES (:id, :sourceConnectionId, :tenantId, :grantedBy, :grantedAt)
                ON CONFLICT (source_connection_id, tenant_id) DO NOTHING
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", access.id())
                .addValue("sourceConnectionId", access.sourceConnectionId())
                .addValue("tenantId", access.tenantId())
                .addValue("grantedBy", access.grantedBy())
                .addValue("grantedAt", access.grantedAt()));
    }

    @Override
    public void revokeAccess(String sourceConnectionId, String tenantId) {
        jdbc.update("""
                DELETE FROM source_connection_access
                WHERE source_connection_id = :sourceConnectionId AND tenant_id = :tenantId
                """,
                new MapSqlParameterSource()
                        .addValue("sourceConnectionId", sourceConnectionId)
                        .addValue("tenantId", tenantId));
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM source_connections WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    @Override
    public boolean hasAccess(String sourceConnectionId, String tenantId) {
        String sql = """
                SELECT COUNT(*) > 0 FROM source_connection_access
                WHERE source_connection_id = :sourceConnectionId AND tenant_id = :tenantId
                """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql,
                new MapSqlParameterSource()
                        .addValue("sourceConnectionId", sourceConnectionId)
                        .addValue("tenantId", tenantId),
                Boolean.class));
    }

    private SourceConnection mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SourceConnection(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("display_name"),
                ConnectionType.valueOf(rs.getString("connection_type")),
                parseJson(rs.getString("config")),
                rs.getString("credentials_ref"),
                rs.getString("created_by"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise config to JSONB", e);
        }
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise config from JSONB", e);
        }
    }
}
