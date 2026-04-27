package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.workflow.domain.model.OnFailure;
import com.platform.workflow.domain.model.TransitionAction;
import com.platform.workflow.domain.model.TransitionActionType;
import com.platform.workflow.domain.model.TransitionTrigger;
import com.platform.workflow.domain.model.ValidationRule;
import com.platform.workflow.domain.model.WorkflowConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
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

    // ── Parse branch coverage ─────────────────────────────────────────────────

    @Test
    void parsesTransitions_withSystemEventTriggerAndSystemEventType() {
        String json = """
                {
                  "id": "wf-sys", "tenantId": "tenant-1", "workflowType": "SE_TYPE",
                  "initialState": "OPEN", "active": true,
                  "states": [{"name": "OPEN", "terminal": false, "allowedRoles": []}],
                  "transitions": [
                    {
                      "name": "auto-escalate",
                      "fromState": "OPEN", "toState": "OPEN",
                      "trigger": "SYSTEM_EVENT",
                      "systemEventType": "BREACH_THRESHOLD",
                      "allowedRoles": [],
                      "requiresMakerChecker": false,
                      "actions": [], "validationRules": []
                    }
                  ]
                }
                """;
        insertConfig("wf-sys", "tenant-1", "SE_TYPE", json, true);

        WorkflowConfig config =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "SE_TYPE").orElseThrow();

        var transition = config.transitions().get(0);
        assertThat(transition.trigger()).isEqualTo(TransitionTrigger.SYSTEM_EVENT);
        assertThat(transition.systemEventType()).isEqualTo("BREACH_THRESHOLD");
    }

    @Test
    void parsesTransitions_withSlaBreachTrigger() {
        String json = """
                {
                  "id": "wf-sla", "tenantId": "tenant-1", "workflowType": "SLA_TYPE",
                  "initialState": "OPEN", "active": true,
                  "states": [{"name": "OPEN", "terminal": false, "allowedRoles": []}],
                  "transitions": [
                    {
                      "name": "breach",
                      "fromState": "OPEN", "toState": "OPEN",
                      "trigger": "SLA_BREACH",
                      "allowedRoles": [],
                      "requiresMakerChecker": false,
                      "actions": [], "validationRules": []
                    }
                  ]
                }
                """;
        insertConfig("wf-sla", "tenant-1", "SLA_TYPE", json, true);

        WorkflowConfig config =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "SLA_TYPE").orElseThrow();

        assertThat(config.transitions().get(0).trigger()).isEqualTo(TransitionTrigger.SLA_BREACH);
        assertThat(config.transitions().get(0).systemEventType()).isNull();
    }

    @Test
    void parsesActions_withNonNullOnFailureVariants() {
        String json = """
                {
                  "id": "wf-act", "tenantId": "tenant-1", "workflowType": "ACT_TYPE",
                  "initialState": "OPEN", "active": true,
                  "states": [{"name": "OPEN", "terminal": false, "allowedRoles": ["ANALYST"]},
                              {"name": "CLOSED", "terminal": true, "allowedRoles": ["ANALYST"]}],
                  "transitions": [
                    {
                      "name": "close",
                      "fromState": "OPEN", "toState": "CLOSED",
                      "trigger": "USER_ACTION",
                      "allowedRoles": ["ANALYST"],
                      "requiresMakerChecker": false,
                      "actions": [
                        {"type": "HTTP_CALL",    "config": {"url": "http://notify"}, "onFailure": "ROLLBACK_TRANSITION"},
                        {"type": "KAFKA_EVENT",  "config": {"topic": "events"},       "onFailure": "CONTINUE"},
                        {"type": "REASSIGN_GROUP","config": {"targetGroup": "ops"},   "onFailure": "RETRY"}
                      ],
                      "validationRules": []
                    }
                  ]
                }
                """;
        insertConfig("wf-act", "tenant-1", "ACT_TYPE", json, true);

        WorkflowConfig config =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "ACT_TYPE").orElseThrow();

        List<TransitionAction> actions = config.transitions().get(0).actions();
        assertThat(actions).hasSize(3);
        assertThat(actions.get(0).type()).isEqualTo(TransitionActionType.HTTP_CALL);
        assertThat(actions.get(0).onFailure()).isEqualTo(OnFailure.ROLLBACK_TRANSITION);
        assertThat(actions.get(1).type()).isEqualTo(TransitionActionType.KAFKA_EVENT);
        assertThat(actions.get(1).onFailure()).isEqualTo(OnFailure.CONTINUE);
        assertThat(actions.get(2).type()).isEqualTo(TransitionActionType.REASSIGN_GROUP);
        assertThat(actions.get(2).onFailure()).isEqualTo(OnFailure.RETRY);
        assertThat(actions.get(2).config()).containsEntry("targetGroup", "ops");
    }

    @Test
    void parsesActions_withNullOnFailure() {
        String json = """
                {
                  "id": "wf-nof", "tenantId": "tenant-1", "workflowType": "NOF_TYPE",
                  "initialState": "OPEN", "active": true,
                  "states": [{"name": "OPEN", "terminal": false, "allowedRoles": ["ANALYST"]},
                              {"name": "CLOSED", "terminal": true, "allowedRoles": ["ANALYST"]}],
                  "transitions": [
                    {
                      "name": "close",
                      "fromState": "OPEN", "toState": "CLOSED",
                      "trigger": "USER_ACTION",
                      "allowedRoles": ["ANALYST"],
                      "requiresMakerChecker": false,
                      "actions": [
                        {"type": "EMAIL_NOTIFICATION", "config": {"to": "ops@co"}}
                      ],
                      "validationRules": []
                    }
                  ]
                }
                """;
        insertConfig("wf-nof", "tenant-1", "NOF_TYPE", json, true);

        WorkflowConfig config =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "NOF_TYPE").orElseThrow();

        TransitionAction action = config.transitions().get(0).actions().get(0);
        assertThat(action.type()).isEqualTo(TransitionActionType.EMAIL_NOTIFICATION);
        assertThat(action.onFailure()).isNull();
    }

    @Test
    void parsesTransitions_withValidationRules() {
        String json = """
                {
                  "id": "wf-vr", "tenantId": "tenant-1", "workflowType": "VR_TYPE",
                  "initialState": "OPEN", "active": true,
                  "states": [{"name": "OPEN", "terminal": false, "allowedRoles": ["ANALYST"]},
                              {"name": "CLOSED", "terminal": true, "allowedRoles": ["ANALYST"]}],
                  "transitions": [
                    {
                      "name": "close",
                      "fromState": "OPEN", "toState": "CLOSED",
                      "trigger": "USER_ACTION",
                      "allowedRoles": ["ANALYST"],
                      "requiresMakerChecker": false,
                      "actions": [],
                      "validationRules": [
                        {"field": "tradeRef",    "operator": "EXISTS", "value": null},
                        {"field": "counterparty", "operator": "NEQ",    "value": "UNKNOWN"}
                      ]
                    }
                  ]
                }
                """;
        insertConfig("wf-vr", "tenant-1", "VR_TYPE", json, true);

        WorkflowConfig config =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "VR_TYPE").orElseThrow();

        List<ValidationRule> rules = config.transitions().get(0).validationRules();
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).field()).isEqualTo("tradeRef");
        assertThat(rules.get(0).operator()).isEqualTo("EXISTS");
        assertThat(rules.get(0).value()).isNull();
        assertThat(rules.get(1).field()).isEqualTo("counterparty");
        assertThat(rules.get(1).operator()).isEqualTo("NEQ");
        assertThat(rules.get(1).value()).isEqualTo("UNKNOWN");
    }

    @Test
    void parsesTransitions_withRequiresMakerChecker() {
        String json = """
                {
                  "id": "wf-mk", "tenantId": "tenant-1", "workflowType": "MK_TYPE",
                  "initialState": "OPEN", "active": true,
                  "states": [{"name": "OPEN", "terminal": false, "allowedRoles": ["ANALYST"]},
                              {"name": "APPROVED", "terminal": true, "allowedRoles": ["ANALYST", "SUPERVISOR"]}],
                  "transitions": [
                    {
                      "name": "approve",
                      "fromState": "OPEN", "toState": "APPROVED",
                      "trigger": "USER_ACTION",
                      "allowedRoles": ["ANALYST", "SUPERVISOR"],
                      "requiresMakerChecker": true,
                      "actions": [], "validationRules": []
                    }
                  ]
                }
                """;
        insertConfig("wf-mk", "tenant-1", "MK_TYPE", json, true);

        WorkflowConfig config =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "MK_TYPE").orElseThrow();

        assertThat(config.transitions().get(0).requiresMakerChecker()).isTrue();
    }

    @Test
    void parsesConfig_withNullStatesAndTransitions() {
        String json = """
                {
                  "id": "wf-empty", "tenantId": "tenant-1", "workflowType": "EMPTY_TYPE",
                  "initialState": "OPEN", "active": true
                }
                """;
        insertConfig("wf-empty", "tenant-1", "EMPTY_TYPE", json, true);

        WorkflowConfig config =
                repository.findActiveByTenantAndWorkflowType("tenant-1", "EMPTY_TYPE").orElseThrow();

        assertThat(config.states()).isEmpty();
        assertThat(config.transitions()).isEmpty();
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
