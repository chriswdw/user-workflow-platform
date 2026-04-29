package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
