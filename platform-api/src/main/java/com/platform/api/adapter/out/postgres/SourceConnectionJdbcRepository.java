package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.domain.ports.out.ISourceConnectionRepository;
import com.platform.domain.model.ConnectionConfig;
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

    private static final String COL_TENANT_ID = "tenantId";
    private static final String COL_SOURCE_CONNECTION_ID = "sourceConnectionId";

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
                .addValue("config", configToJson(c.config()))
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
                .addValue(COL_TENANT_ID, tenantId)
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
                .addValue(COL_SOURCE_CONNECTION_ID, access.sourceConnectionId())
                .addValue(COL_TENANT_ID, access.tenantId())
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
                        .addValue(COL_SOURCE_CONNECTION_ID, sourceConnectionId)
                        .addValue(COL_TENANT_ID, tenantId));
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
                        .addValue(COL_SOURCE_CONNECTION_ID, sourceConnectionId)
                        .addValue(COL_TENANT_ID, tenantId),
                Boolean.class));
    }

    private SourceConnection mapRow(ResultSet rs, int rowNum) throws SQLException {
        ConnectionType type = ConnectionType.valueOf(rs.getString("connection_type"));
        return new SourceConnection(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("display_name"),
                type,
                parseConfig(rs.getString("config"), type),
                rs.getString("credentials_ref"),
                rs.getString("created_by"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String configToJson(ConnectionConfig config) {
        try {
            Map<String, Object> map = switch (config) {
                case ConnectionConfig.KafkaConfig(var bootstrapServers, var topicName) -> Map.of(
                        "type", "KAFKA",
                        "bootstrapServers", bootstrapServers,
                        "topicName", topicName);
                case ConnectionConfig.DbPollConfig(var jdbcUrl, var query, var pollIntervalSeconds) -> Map.of(
                        "type", "DB_POLL",
                        "jdbcUrl", jdbcUrl,
                        "query", query,
                        "pollIntervalSeconds", pollIntervalSeconds);
                case ConnectionConfig.FileShareConfig(var path, var filePattern) -> Map.of(
                        "type", "FILE_SHARE",
                        "path", path,
                        "filePattern", filePattern);
            };
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise config to JSONB", e);
        }
    }

    private ConnectionConfig parseConfig(String json, ConnectionType type) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return switch (type) {
                case KAFKA -> new ConnectionConfig.KafkaConfig(
                        (String) map.get("bootstrapServers"),
                        (String) map.get("topicName"));
                case DB_POLL -> new ConnectionConfig.DbPollConfig(
                        (String) map.get("jdbcUrl"),
                        (String) map.get("query"),
                        ((Number) map.getOrDefault("pollIntervalSeconds", 60)).intValue());
                case FILE_SHARE -> new ConnectionConfig.FileShareConfig(
                        (String) map.get("path"),
                        (String) map.get("filePattern"));
            };
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise config from JSONB", e);
        }
    }
}
