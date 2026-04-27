package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.ports.out.IConfigDocumentRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ConfigDocumentJdbcRepository implements IConfigDocumentRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ConfigDocumentJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ConfigDocument> findByTenantAndWorkflowTypeAndType(String tenantId,
                                                                    String workflowType,
                                                                    ConfigType configType) {
        String sql = """
                SELECT * FROM config_documents
                WHERE tenant_id = :tenantId
                  AND workflow_type = :workflowType
                  AND config_type = :configType
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("workflowType", workflowType)
                .addValue("configType", configType.name());
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public List<ConfigDocument> findAllActiveByTenant(String tenantId) {
        String sql = """
                SELECT * FROM config_documents
                WHERE tenant_id = :tenantId AND active = true
                """;
        var params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        return jdbc.query(sql, params, this::mapRow);
    }

    private ConfigDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ConfigDocument(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("workflow_type"),
                ConfigType.valueOf(rs.getString("config_type")),
                parseJson(rs.getString("content")),
                rs.getString("version"),
                rs.getBoolean("active")
        );
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise config document content from JSONB", e);
        }
    }
}
