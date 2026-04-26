package com.platform.routing.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
import com.platform.routing.domain.model.ConditionNode;
import com.platform.routing.domain.model.GroupCondition;
import com.platform.routing.domain.model.LeafCondition;
import com.platform.routing.domain.model.LogicalOperator;
import com.platform.routing.domain.model.Operator;
import com.platform.routing.domain.model.RoutingConfig;
import com.platform.routing.domain.model.RoutingRule;
import com.platform.routing.domain.model.RoutingResult;
import com.platform.routing.domain.model.WorkItemToRoute;
import com.platform.routing.domain.ports.in.IRouteWorkItemUseCase;
import com.platform.routing.domain.service.RoutingService;
import com.platform.routing.doubles.InMemoryAuditRepository;
import com.platform.routing.doubles.InMemoryRoutingConfigRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions for the routing feature.
 * Cucumber creates a new instance per scenario, so all instance fields are per-scenario state.
 * The domain service is wired with in-memory doubles — no Spring context, no database.
 */
public class RoutingStepDefinitions {

    // In-memory doubles — fresh per scenario
    private final InMemoryRoutingConfigRepository routingConfigRepo = new InMemoryRoutingConfigRepository();
    private final InMemoryAuditRepository auditRepo = new InMemoryAuditRepository();
    private final IRouteWorkItemUseCase routingService = new RoutingService(routingConfigRepo, auditRepo);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Scenario state
    private String tenantId;
    private String workflowType;
    private String defaultGroupId;
    private final List<RoutingRule> pendingRules = new ArrayList<>();
    private int ruleCounter = 0;
    private RoutingResult result;

    // ── Given ───────────────────────────────────────────────────────────────

    @Given("a tenant {string} with id {string}")
    public void aTenantWithId(String name, String id) {
        this.tenantId = id;
    }

    @Given("a resolution group {string} with id {string}")
    public void aResolutionGroupWithId(String name, String id) {
        // Groups are referenced by id in routing config; nothing to store here.
        // This step exists for readability — the ids are used directly in rule steps.
    }

    @Given("a routing config for workflow type {string} with default group {string}")
    public void aRoutingConfigForWorkflowType(String workflowType, String defaultGroupId) {
        this.workflowType = workflowType;
        this.defaultGroupId = defaultGroupId;
        this.pendingRules.clear();
    }

    @Given("the config has a rule with priority {int} matching {string} EQ {string} routing to {string}")
    public void addSimpleEqRule(int priority, String field, String value, String targetGroupId) {
        addLeafRule(priority, field, Operator.EQ, value, targetGroupId);
    }

    @Given("the config has a rule with priority {int} matching {string} NEQ {string} routing to {string}")
    public void addSimpleNeqRule(int priority, String field, String value, String targetGroupId) {
        addLeafRule(priority, field, Operator.NEQ, value, targetGroupId);
    }

    @Given("the config has a rule with priority {int} matching {string} CONTAINS {string} routing to {string}")
    public void addSimpleContainsRule(int priority, String field, String value, String targetGroupId) {
        addLeafRule(priority, field, Operator.CONTAINS, value, targetGroupId);
    }

    @Given("the config has a rule with priority {int} matching {string} REGEX {string} routing to {string}")
    public void addSimpleRegexRule(int priority, String field, String value, String targetGroupId) {
        addLeafRule(priority, field, Operator.REGEX, value, targetGroupId);
    }

    @Given("the config has a rule with priority {int} matching {string} LT {string} routing to {string}")
    public void addSimpleLtRule(int priority, String field, String value, String targetGroupId) {
        addLeafRule(priority, field, Operator.LT, value, targetGroupId);
    }

    @Given("the config has a rule with priority {int} matching {string} LTE {string} routing to {string}")
    public void addSimpleLteRule(int priority, String field, String value, String targetGroupId) {
        addLeafRule(priority, field, Operator.LTE, value, targetGroupId);
    }

    private void addLeafRule(int priority, String field, Operator operator, String value, String targetGroupId) {
        pendingRules.add(new RoutingRule(
                "rule-" + (++ruleCounter),
                "rule-" + ruleCounter,
                priority,
                new LeafCondition(field, operator, value),
                targetGroupId,
                true
        ));
    }

    @Given("the config has an inactive rule with priority {int} matching {string} EQ {string} routing to {string}")
    public void addInactiveSimpleEqRule(int priority, String field, String value, String targetGroupId) {
        pendingRules.add(new RoutingRule(
                "rule-" + (++ruleCounter),
                "rule-" + ruleCounter,
                priority,
                new LeafCondition(field, Operator.EQ, value),
                targetGroupId,
                false
        ));
    }

    @Given("the config has a rule with priority {int} routing to {string} matching:")
    public void addComplexRule(int priority, String targetGroupId, String conditionJson) throws Exception {
        ConditionNode condition = parseConditionNode(objectMapper.readTree(conditionJson));
        pendingRules.add(new RoutingRule(
                "rule-" + (++ruleCounter),
                "rule-" + ruleCounter,
                priority,
                condition,
                targetGroupId,
                true
        ));
    }

    // ── When ────────────────────────────────────────────────────────────────

    @When("a work item is routed with fields:")
    public void aWorkItemIsRoutedWithFields(DataTable dataTable) {
        // Build and save the routing config from accumulated rules
        List<RoutingRule> sortedRules = pendingRules.stream()
                .sorted(Comparator.comparingInt(RoutingRule::priority))
                .toList();

        routingConfigRepo.save(new RoutingConfig(
                "config-1",
                tenantId,
                workflowType,
                defaultGroupId,
                false,
                sortedRules
        ));

        // Build nested fields map from the data table (dot-notation keys → values)
        Map<String, Object> fields = buildNestedMap(dataTable.asMap());

        result = routingService.route(new WorkItemToRoute(
                "work-item-001",
                tenantId,
                workflowType,
                "system",
                fields
        ));
    }

    // ── Then ────────────────────────────────────────────────────────────────

    @Then("the work item is assigned to {string}")
    public void theWorkItemIsAssignedTo(String expectedGroupId) {
        assertThat(result.assignedGroupId())
                .as("assigned group")
                .isEqualTo(expectedGroupId);
    }

    @Then("routedByDefault is {word}")
    public void routedByDefaultIs(String expected) {
        assertThat(result.routedByDefault())
                .as("routedByDefault")
                .isEqualTo(Boolean.parseBoolean(expected));
    }

    @Then("an audit entry is written with event type {string}")
    public void anAuditEntryIsWrittenWithEventType(String eventType) {
        AuditEventType expectedType = AuditEventType.valueOf(eventType);
        assertThat(auditRepo.all())
                .as("audit entries")
                .anyMatch(e -> e.eventType() == expectedType);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds a nested Map from a flat map of dot-notation keys to string values.
     * "counterparty.region" → EMEA becomes fields["counterparty"]["region"] = "EMEA"
     */
    private Map<String, Object> buildNestedMap(Map<String, String> flatMap) {
        Map<String, Object> nested = new HashMap<>();
        for (Map.Entry<String, String> entry : flatMap.entrySet()) {
            setNestedValue(nested, entry.getKey(), entry.getValue());
        }
        return nested;
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> map, String dotPath, Object value) {
        int dot = dotPath.indexOf('.');
        if (dot == -1) {
            map.put(dotPath, value);
        } else {
            String head = dotPath.substring(0, dot);
            String tail = dotPath.substring(dot + 1);
            map.computeIfAbsent(head, k -> new HashMap<>());
            setNestedValue((Map<String, Object>) map.get(head), tail, value);
        }
    }

    /**
     * Parses a JSON condition node (LEAF or GROUP) into the ConditionNode sealed hierarchy.
     */
    private ConditionNode parseConditionNode(JsonNode node) {
        String type = node.get("type").asText();
        return switch (type) {
            case "LEAF" -> new LeafCondition(
                    node.get("field").asText(),
                    Operator.valueOf(node.get("operator").asText()),
                    parseLeafValue(node.get("value"))
            );
            case "GROUP" -> new GroupCondition(
                    LogicalOperator.valueOf(node.get("logicalOperator").asText()),
                    StreamSupport.stream(node.get("children").spliterator(), false)
                            .map(this::parseConditionNode)
                            .toList()
            );
            default -> throw new IllegalArgumentException("Unknown condition node type: " + type);
        };
    }

    private static Object parseLeafValue(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isArray()) {
            return StreamSupport.stream(valueNode.spliterator(), false)
                    .map(JsonNode::asText)
                    .toList();
        }
        return valueNode.asText();
    }
}
