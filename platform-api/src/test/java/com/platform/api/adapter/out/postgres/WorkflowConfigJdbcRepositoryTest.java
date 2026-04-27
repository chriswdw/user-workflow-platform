package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.workflow.domain.model.WorkflowConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowConfigJdbcRepositoryTest {

    private static final String WORKFLOW_CONFIG_JSON = """
            {
              "id": "wf-test-1",
              "tenantId": "tenant-1",
              "workflowType": "TEST_TYPE",
              "initialState": "OPEN",
              "active": true,
              "states": [
                {"name": "OPEN",   "terminal": false, "allowedRoles": ["ANALYST"]},
                {"name": "CLOSED", "terminal": true,  "allowedRoles": ["ANALYST", "SUPERVISOR"]}
              ],
              "transitions": [
                {
                  "name": "close",
                  "fromState": "OPEN",
                  "toState": "CLOSED",
                  "trigger": "USER_ACTION",
                  "allowedRoles": ["ANALYST"],
                  "requiresMakerChecker": false,
                  "actions": [],
                  "validationRules": []
                }
              ]
            }
            """;

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WorkflowConfigJdbcRepository repository =
            new WorkflowConfigJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE config_documents", Map.of());
    }

    @Test
    void findActiveByTenantAndWorkflowType_returnsEmptyWhenNotFound() {
        Optional<WorkflowConfig> result =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "UNKNOWN");
        assertThat(result).isEmpty();
    }

    @Test
    void findActiveByTenantAndWorkflowType_returnsEmptyWhenInactive() {
        insertConfig("wf-inactive", "tenant-1", "TEST_TYPE", WORKFLOW_CONFIG_JSON, false);

        assertThat(repository.findActiveByTenantAndWorkflowType("tenant-1", "TEST_TYPE")).isEmpty();
    }

    @Test
    void findActiveByTenantAndWorkflowType_parsesConfigCorrectly() {
        insertConfig("wf-test-1", "tenant-1", "TEST_TYPE", WORKFLOW_CONFIG_JSON, true);

        Optional<WorkflowConfig> result =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "TEST_TYPE");

        assertThat(result).isPresent();
        WorkflowConfig config = result.get();
        assertThat(config.id()).isEqualTo("wf-test-1");
        assertThat(config.workflowType()).isEqualTo("TEST_TYPE");
        assertThat(config.initialState()).isEqualTo("OPEN");
        assertThat(config.active()).isTrue();
        assertThat(config.states()).hasSize(2);
        assertThat(config.transitions()).hasSize(1);
    }

    @Test
    void findActiveByTenantAndWorkflowType_parsesStatesCorrectly() {
        insertConfig("wf-test-1", "tenant-1", "TEST_TYPE", WORKFLOW_CONFIG_JSON, true);

        WorkflowConfig config =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "TEST_TYPE").orElseThrow();

        var openState = config.states().get(0);
        assertThat(openState.name()).isEqualTo("OPEN");
        assertThat(openState.terminal()).isFalse();
        assertThat(openState.allowedRoles()).containsExactly("ANALYST");

        var closedState = config.states().get(1);
        assertThat(closedState.terminal()).isTrue();
    }

    @Test
    void findActiveByTenantAndWorkflowType_parsesTransitionsCorrectly() {
        insertConfig("wf-test-1", "tenant-1", "TEST_TYPE", WORKFLOW_CONFIG_JSON, true);

        WorkflowConfig config =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "TEST_TYPE").orElseThrow();

        var transition = config.transitions().get(0);
        assertThat(transition.name()).isEqualTo("close");
        assertThat(transition.fromState()).isEqualTo("OPEN");
        assertThat(transition.toState()).isEqualTo("CLOSED");
        assertThat(transition.allowedRoles()).containsExactly("ANALYST");
        assertThat(transition.requiresMakerChecker()).isFalse();
        assertThat(transition.actions()).isEmpty();
        assertThat(transition.validationRules()).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void insertConfig(String id, String tenantId, String workflowType,
                                      String contentJson, boolean active) {
        jdbc.update("""
                INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
                VALUES (:id, :tenantId, :workflowType, 'WORKFLOW_CONFIG', CAST(:content AS jsonb), '1', :active)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("workflowType", workflowType)
                        .addValue("content", contentJson)
                        .addValue("active", active));
    }
}
