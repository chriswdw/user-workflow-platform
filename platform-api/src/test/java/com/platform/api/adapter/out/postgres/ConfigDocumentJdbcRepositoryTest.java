package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDocumentJdbcRepositoryTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ConfigDocumentJdbcRepository repository =
            new ConfigDocumentJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE config_documents", Map.of());
    }

    @Test
    void findByTenantAndWorkflowTypeAndType_returnsEmptyWhenNoneMatch() {
        List<ConfigDocument> result = repository.findByTenantAndWorkflowTypeAndType(
                "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG);
        assertThat(result).isEmpty();
    }

    @Test
    void findByTenantAndWorkflowTypeAndType_returnsMatchingDocuments() {
        insert("doc-1", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG,
                Map.of("key", "value"), true);
        insert("doc-2", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.DETAIL_VIEW_CONFIG,
                Map.of("other", "data"), true);

        List<ConfigDocument> result = repository.findByTenantAndWorkflowTypeAndType(
                "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("doc-1");
        assertThat(result.get(0).configType()).isEqualTo(ConfigType.WORKFLOW_CONFIG);
        assertThat(result.get(0).content()).containsKey("key");
    }

    @Test
    void findByTenantAndWorkflowTypeAndType_returnsAllActiveAndInactive() {
        insert("doc-active",   "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG,
                Map.of("v", "1"), true);
        insert("doc-inactive", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG,
                Map.of("v", "0"), false);

        List<ConfigDocument> result = repository.findByTenantAndWorkflowTypeAndType(
                "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG);

        assertThat(result).hasSize(2)
                .extracting(ConfigDocument::id)
                .containsExactlyInAnyOrder("doc-active", "doc-inactive");
    }

    @Test
    void findAllActiveByTenant_returnsOnlyActiveDocuments() {
        insert("doc-active",   "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG,
                Map.of("v", "1"), true);
        insert("doc-inactive", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.DETAIL_VIEW_CONFIG,
                Map.of("v", "0"), false);

        List<ConfigDocument> result = repository.findAllActiveByTenant("tenant-1");

        assertThat(result).hasSize(1)
                .extracting(ConfigDocument::id)
                .containsExactly("doc-active");
    }

    @Test
    void findAllActiveByTenant_doesNotCrossTenantBoundary() {
        insert("doc-A", "tenant-A", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG,
                Map.of(), true);
        insert("doc-B", "tenant-B", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG,
                Map.of(), true);

        assertThat(repository.findAllActiveByTenant("tenant-A"))
                .extracting(ConfigDocument::id)
                .containsExactly("doc-A");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void insert(String id, String tenantId, String workflowType,
                                ConfigType configType, Map<String, Object> content,
                                boolean active) {
        try {
            String json = objectMapper.writeValueAsString(content);
            jdbc.update("""
                    INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
                    VALUES (:id, :tenantId, :workflowType, :configType, CAST(:content AS jsonb), '1', :active)
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("tenantId", tenantId)
                            .addValue("workflowType", workflowType)
                            .addValue("configType", configType.name())
                            .addValue("content", json)
                            .addValue("active", active));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
