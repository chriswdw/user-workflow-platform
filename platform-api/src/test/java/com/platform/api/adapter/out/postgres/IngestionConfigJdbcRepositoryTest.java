package com.platform.api.adapter.out.postgres;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.platform.domain.model.SourceType;
import com.platform.ingestion.domain.model.IdempotencyKeyStrategy;
import com.platform.ingestion.domain.model.IngestionConfig;
import com.platform.ingestion.domain.model.UnknownColumnPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionConfigJdbcRepositoryTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new JsonMapper();

    private final IngestionConfigJdbcRepository repository =
            new IngestionConfigJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE config_documents CASCADE", Map.of());
    }

    @Test
    void findByTenantAndWorkflowTypeAndSourceType_returnsEmptyWhenNoneConfigured() {
        Optional<IngestionConfig> result = repository.findByTenantAndWorkflowTypeAndSourceType(
                "tenant-1", "SETTLEMENT_EXCEPTION", SourceType.KAFKA);

        assertThat(result).isEmpty();
    }

    @Test
    void findByTenantAndWorkflowTypeAndSourceType_returnsConfigWhenPresent() {
        insertIngestionConfig("tenant-1", "SETTLEMENT_EXCEPTION", "KAFKA", "tradeRef", true);

        Optional<IngestionConfig> result = repository.findByTenantAndWorkflowTypeAndSourceType(
                "tenant-1", "SETTLEMENT_EXCEPTION", SourceType.KAFKA);

        assertThat(result).isPresent();
        IngestionConfig config = result.get();
        assertThat(config.tenantId()).isEqualTo("tenant-1");
        assertThat(config.workflowType()).isEqualTo("SETTLEMENT_EXCEPTION");
        assertThat(config.sourceType()).isEqualTo(SourceType.KAFKA);
        assertThat(config.idempotencyExplicitField()).isEqualTo("tradeRef");
        assertThat(config.idempotencyKeyStrategy()).isEqualTo(IdempotencyKeyStrategy.EXPLICIT_FIELD);
        assertThat(config.unknownColumnPolicy()).isEqualTo(UnknownColumnPolicy.IGNORE);
        assertThat(config.initialState()).isEqualTo("UNDER_REVIEW");
        assertThat(config.fieldMappings()).hasSize(1);
        assertThat(config.fieldMappings().get(0).sourceField()).isEqualTo("tradeRef");
        assertThat(config.fieldMappings().get(0).targetField()).isEqualTo("trade.ref");
        assertThat(config.fieldMappings().get(0).required()).isTrue();
    }

    @Test
    void findByTenantAndWorkflowTypeAndSourceType_doesNotReturnInactiveConfig() {
        insertIngestionConfig("tenant-1", "SETTLEMENT_EXCEPTION", "KAFKA", "tradeRef", false);

        assertThat(repository.findByTenantAndWorkflowTypeAndSourceType(
                "tenant-1", "SETTLEMENT_EXCEPTION", SourceType.KAFKA)).isEmpty();
    }

    @Test
    void findByTenantAndWorkflowTypeAndSourceType_doesNotCrossSourceTypeBoundary() {
        insertIngestionConfig("tenant-1", "SETTLEMENT_EXCEPTION", "KAFKA", "tradeRef", true);

        assertThat(repository.findByTenantAndWorkflowTypeAndSourceType(
                "tenant-1", "SETTLEMENT_EXCEPTION", SourceType.FILE_UPLOAD)).isEmpty();
    }

    @Test
    void findByTenantAndWorkflowTypeAndSourceType_doesNotCrossTenantBoundary() {
        insertIngestionConfig("tenant-A", "SETTLEMENT_EXCEPTION", "KAFKA", "tradeRef", true);

        assertThat(repository.findByTenantAndWorkflowTypeAndSourceType(
                "tenant-B", "SETTLEMENT_EXCEPTION", SourceType.KAFKA)).isEmpty();
    }

    // NOTE: parseContent()'s JacksonException catch block (IngestionConfigJdbcRepository.java
    // lines 74-75) is not covered here. The query's WHERE clause requires
    // content->>'sourceType' = :sourceType to match, which is only non-null when content is a
    // JSON *object* exposing that key — and any JSON object deserialises successfully into
    // Map<String,Object> (Jackson's generic-Map target is permissive about nested shapes). A
    // non-object payload (array/scalar) that would actually fail deserialisation can never reach
    // parseContent() through this query, since it can't satisfy the WHERE filter first. This
    // catch block is defensive dead code under the current query shape, not a reachable gap —
    // left uncovered deliberately rather than adding a test that doesn't exercise a real path.

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void insertIngestionConfig(String tenantId, String workflowType,
                                               String sourceType, String idempotencyField,
                                               boolean active) {
        String content;
        try {
            content = objectMapper.writeValueAsString(Map.of(
                    "tenantId", tenantId,
                    "workflowType", workflowType,
                    "sourceType", sourceType,
                    "fieldMappings", java.util.List.of(
                            Map.of("sourceField", idempotencyField,
                                   "targetField", "trade.ref",
                                   "required", true)
                    ),
                    "unknownColumnPolicy", "IGNORE",
                    "idempotencyKeyStrategy", "EXPLICIT_FIELD",
                    "idempotencyKeyFields", java.util.List.of(),
                    "idempotencyExplicitField", idempotencyField,
                    "initialState", "UNDER_REVIEW"
            ));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        jdbc.update("""
                INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
                VALUES (:id, :tenantId, :workflowType, 'INGESTION_SOURCE_CONFIG', CAST(:content AS jsonb), '1', :active)
                """,
                new MapSqlParameterSource()
                        .addValue("id", "test-ing-" + tenantId + "-" + sourceType)
                        .addValue("tenantId", tenantId)
                        .addValue("workflowType", workflowType)
                        .addValue("content", content)
                        .addValue("active", active));
    }
}
