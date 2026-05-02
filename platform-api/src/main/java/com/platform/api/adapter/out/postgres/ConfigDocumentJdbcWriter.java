package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.ports.out.IConfigDocumentWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

public class ConfigDocumentJdbcWriter implements IConfigDocumentWriter {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ConfigDocumentJdbcWriter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveAll(List<ConfigDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO config_documents
                    (id, tenant_id, workflow_type, config_type, content, version, active)
                VALUES (:id, :tenantId, :workflowType, :configType, CAST(:content AS jsonb), :version, true)
                ON CONFLICT (id) DO NOTHING
                """;
        SqlParameterSource[] params = documents.stream()
                .map(doc -> new MapSqlParameterSource()
                        .addValue("id", doc.id())
                        .addValue("tenantId", doc.tenantId())
                        .addValue("workflowType", doc.workflowType())
                        .addValue("configType", doc.configType().name())
                        .addValue("content", toJson(doc.content()))
                        .addValue("version", doc.version()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(sql, params);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise config document content to JSONB", e);
        }
    }
}
