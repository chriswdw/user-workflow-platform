package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.domain.model.ConfigType;
import com.platform.domain.model.SourceType;
import com.platform.ingestion.domain.model.FieldMapping;
import com.platform.ingestion.domain.model.IdempotencyKeyStrategy;
import com.platform.ingestion.domain.model.IngestionConfig;
import com.platform.ingestion.domain.model.UnknownColumnPolicy;
import com.platform.ingestion.domain.ports.out.IIngestionConfigRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class IngestionConfigJdbcRepository implements IIngestionConfigRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IngestionConfigJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<IngestionConfig> findByTenantAndWorkflowTypeAndSourceType(
            String tenantId, String workflowType, SourceType sourceType) {
        String sql = """
                SELECT content FROM config_documents
                WHERE tenant_id = :tenantId
                  AND workflow_type = :workflowType
                  AND config_type = :configType
                  AND active = true
                  AND content->>'sourceType' = :sourceType
                LIMIT 1
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("workflowType", workflowType)
                .addValue("configType", ConfigType.INGESTION_SOURCE_CONFIG.name())
                .addValue("sourceType", sourceType.name());
        return jdbc.query(sql, params, (rs, n) -> parseContent(rs.getString("content")))
                .stream().findFirst();
    }

    @SuppressWarnings("unchecked")
    private IngestionConfig parseContent(String json) {
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, Object>> rawMappings = (List<Map<String, Object>>) m.getOrDefault("fieldMappings", List.of());
            List<FieldMapping> fieldMappings = rawMappings.stream()
                    .map(fm -> new FieldMapping(
                            (String) fm.get("sourceField"),
                            (String) fm.get("targetField"),
                            Boolean.TRUE.equals(fm.get("required"))))
                    .toList();
            List<String> idempotencyKeyFields = (List<String>) m.getOrDefault("idempotencyKeyFields", List.of());
            return new IngestionConfig(
                    (String) m.get("tenantId"),
                    (String) m.get("workflowType"),
                    SourceType.valueOf((String) m.get("sourceType")),
                    fieldMappings,
                    UnknownColumnPolicy.valueOf((String) m.getOrDefault("unknownColumnPolicy", "IGNORE")),
                    IdempotencyKeyStrategy.valueOf((String) m.getOrDefault("idempotencyKeyStrategy", "EXPLICIT_FIELD")),
                    idempotencyKeyFields,
                    (String) m.get("idempotencyExplicitField"),
                    (String) m.get("initialState")
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise IngestionConfig from JSONB", e);
        }
    }
}
