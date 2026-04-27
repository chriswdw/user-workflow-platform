package com.platform.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.api.adapter.out.postgres.ConfigDocumentJdbcRepository;
import com.platform.api.adapter.out.postgres.EmbeddedPostgresProvider;
import com.platform.config.domain.exception.ConfigIntegrityException;
import com.platform.config.domain.exception.ConfigNotFoundException;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.model.ConfigValidationResult;
import com.platform.config.domain.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test wiring ConfigService with the real ConfigDocumentJdbcRepository.
 *
 * The platform-config-engine JaCoCo report is empty because its Cucumber tests use
 * an in-memory double — ConfigService itself never runs under coverage. This test
 * exercises ConfigService against a real PostgreSQL instance to fill that gap and
 * to validate the cross-schema constraint logic that in-memory tests cannot detect
 * (e.g. duplicate active configs violating the DB state that loadActive() checks).
 */
class ConfigServiceIntegrationTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ConfigDocumentJdbcRepository repository =
            new ConfigDocumentJdbcRepository(jdbc, objectMapper);
    private final ConfigService service = new ConfigService(repository);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE config_documents", Map.of());
    }

    // ── loadActive ───────────────────────────────────────────────────────────

    @Test
    void loadActive_returnsTheSingleActiveDocument() {
        insertConfig("cfg-active",   "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG, true);
        insertConfig("cfg-inactive", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG, false);

        ConfigDocument result = service.loadActive("tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG);

        assertThat(result.id()).isEqualTo("cfg-active");
        assertThat(result.active()).isTrue();
    }

    @Test
    void loadActive_throwsConfigNotFoundWhenNoActiveDocumentExists() {
        insertConfig("cfg-inactive", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG, false);

        assertThatThrownBy(() ->
                service.loadActive("tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("WORKFLOW_CONFIG")
                .hasMessageContaining("tenant-1");
    }

    @Test
    void loadActive_throwsConfigNotFoundWhenNothingExistsForTenant() {
        assertThatThrownBy(() ->
                service.loadActive("unknown-tenant", "ANY_TYPE", ConfigType.ROUTING_CONFIG))
                .isInstanceOf(ConfigNotFoundException.class);
    }

    @Test
    void loadActive_throwsConfigIntegrityExceptionWhenMultipleActiveDocumentsExist() {
        insertConfig("cfg-v1", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG, true);
        insertConfig("cfg-v2", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG, true);

        assertThatThrownBy(() ->
                service.loadActive("tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG))
                .isInstanceOf(ConfigIntegrityException.class)
                .hasMessageContaining("Multiple active")
                .hasMessageContaining("WORKFLOW_CONFIG");
    }

    // ── validate ─────────────────────────────────────────────────────────────

    @Test
    void validate_returnsNoViolationsWhenConfigsAreConsistent() {
        insertConfig("rc-1", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.ROUTING_CONFIG, true,
                Map.of("defaultGroup", "ops-group"));
        insertConfig("rg-ops", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.RESOLUTION_GROUP, true,
                Map.of("id", "ops-group", "name", "Ops Team"));

        ConfigValidationResult result = service.validate("tenant-1");

        assertThat(result.isValid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void validate_detectsRoutingConfigDefaultGroupReferencingNonExistentResolutionGroup() {
        insertConfig("rc-1", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.ROUTING_CONFIG, true,
                Map.of("defaultGroup", "orphaned-group"));

        ConfigValidationResult result = service.validate("tenant-1");

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).message())
                .contains("orphaned-group")
                .contains("does not reference an active resolution group");
        assertThat(result.violations().get(0).configType())
                .isEqualTo(ConfigType.ROUTING_CONFIG);
    }

    @Test
    void validate_detectsDuplicateActiveConfigsForSameWorkflowType() {
        insertConfig("wc-v1", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG, true);
        insertConfig("wc-v2", "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG, true);

        ConfigValidationResult result = service.validate("tenant-1");

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).message())
                .contains("duplicate active")
                .contains("WORKFLOW_CONFIG");
        assertThat(result.violations().get(0).workflowType())
                .isEqualTo("SETTLEMENT_EXCEPTION");
    }

    @Test
    void validate_doesNotReportViolationsForDifferentTenants() {
        // tenant-B has an orphaned defaultGroup — tenant-A's validation must not be affected
        insertConfig("rc-B", "tenant-B", "SETTLEMENT_EXCEPTION", ConfigType.ROUTING_CONFIG, true,
                Map.of("defaultGroup", "orphaned"));
        insertConfig("rc-A", "tenant-A", "SETTLEMENT_EXCEPTION", ConfigType.ROUTING_CONFIG, true,
                Map.of("defaultGroup", "ops-group"));
        insertConfig("rg-A", "tenant-A", "SETTLEMENT_EXCEPTION", ConfigType.RESOLUTION_GROUP, true,
                Map.of("id", "ops-group"));

        assertThat(service.validate("tenant-A").isValid()).isTrue();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void insertConfig(String id, String tenantId, String workflowType,
                                      ConfigType type, boolean active) {
        insertConfig(id, tenantId, workflowType, type, active, Map.of());
    }

    private static void insertConfig(String id, String tenantId, String workflowType,
                                      ConfigType type, boolean active, Map<String, Object> content) {
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
                            .addValue("configType", type.name())
                            .addValue("content", json)
                            .addValue("active", active));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
