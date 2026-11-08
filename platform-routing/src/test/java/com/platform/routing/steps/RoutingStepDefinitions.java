package com.platform.routing.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.domain.model.AuditEventType;
import com.platform.routing.domain.model.ConditionNode;
import com.platform.routing.domain.model.EffectivePeriod;
import com.platform.routing.domain.model.GroupCondition;
import com.platform.routing.domain.model.LeafCondition;
import com.platform.routing.domain.model.LogicalOperator;
import com.platform.routing.domain.model.Operator;
import com.platform.routing.domain.model.RoutingConfig;
import com.platform.routing.domain.model.RoutingResult;
import com.platform.routing.domain.model.RoutingRule;
import com.platform.routing.domain.model.WorkItemToRoute;
import com.platform.routing.domain.ports.in.IRouteWorkItemUseCase;
import com.platform.routing.domain.service.RoutingService;
import com.platform.routing.doubles.InMemoryAuditRepository;
import com.platform.routing.doubles.InMemoryRoutingConfigRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cucumber step definitions for the routing feature. Cucumber creates a new instance per scenario,
 * so all instance fields are per-scenario state. The domain service is wired with in-memory doubles
 * — no Spring context, no database.
 */
public class RoutingStepDefinitions {

  // In-memory doubles — fresh per scenario
  private final InMemoryRoutingConfigRepository routingConfigRepo =
      new InMemoryRoutingConfigRepository();
  private final InMemoryAuditRepository auditRepo = new InMemoryAuditRepository();
  private final ObjectMapper objectMapper = new JsonMapper();

  // Scenario state
  private Clock clock = Clock.systemUTC();
  private String tenantId;
  private String workflowType;
  private String defaultGroupId;
  private final List<RoutingRule> pendingRules = new ArrayList<>();
  private int ruleCounter = 0;
  private RoutingResult result;
  private Exception routingFailure;

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

  @Given(
      "the config has a rule with priority {int} matching {string} EQ {string} routing to {string}")
  public void addSimpleEqRule(int priority, String field, String value, String targetGroupId) {
    addLeafRule(priority, field, Operator.EQ, value, targetGroupId);
  }

  @Given(
      "the config has a rule with priority {int} matching {string} NEQ {string} routing to {string}")
  public void addSimpleNeqRule(int priority, String field, String value, String targetGroupId) {
    addLeafRule(priority, field, Operator.NEQ, value, targetGroupId);
  }

  @Given(
      "the config has a rule with priority {int} matching {string} CONTAINS {string} routing to {string}")
  public void addSimpleContainsRule(
      int priority, String field, String value, String targetGroupId) {
    addLeafRule(priority, field, Operator.CONTAINS, value, targetGroupId);
  }

  @Given(
      "the config has a rule with priority {int} matching {string} REGEX {string} routing to {string}")
  public void addSimpleRegexRule(int priority, String field, String value, String targetGroupId) {
    addLeafRule(priority, field, Operator.REGEX, value, targetGroupId);
  }

  @Given(
      "the config has a rule with priority {int} matching {string} LT {string} routing to {string}")
  public void addSimpleLtRule(int priority, String field, String value, String targetGroupId) {
    addLeafRule(priority, field, Operator.LT, value, targetGroupId);
  }

  @Given(
      "the config has a rule with priority {int} matching {string} LTE {string} routing to {string}")
  public void addSimpleLteRule(int priority, String field, String value, String targetGroupId) {
    addLeafRule(priority, field, Operator.LTE, value, targetGroupId);
  }

  @Given(
      "the config has a rule with priority {int} matching {string} GT {string} routing to {string}")
  public void addSimpleGtRule(int priority, String field, String value, String targetGroupId) {
    addLeafRule(priority, field, Operator.GT, value, targetGroupId);
  }

  private void addLeafRule(
      int priority, String field, Operator operator, String value, String targetGroupId) {
    addLeafRule(priority, field, operator, value, targetGroupId, EffectivePeriod.unbounded());
  }

  private void addLeafRule(
      int priority,
      String field,
      Operator operator,
      String value,
      String targetGroupId,
      EffectivePeriod effective) {
    pendingRules.add(
        new RoutingRule(
            "rule-" + (++ruleCounter),
            "rule-" + ruleCounter,
            priority,
            new LeafCondition(field, operator, value),
            targetGroupId,
            true,
            effective));
  }

  @Given("the current date is {string}")
  public void theCurrentDateIs(String isoDate) {
    // Midday UTC, so the instant falls unambiguously inside the given calendar date.
    clock =
        Clock.fixed(
            LocalDate.parse(isoDate).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
  }

  @Given(
      "the config has a rule with priority {int} matching {string} EQ {string} routing to {string} effective from {string}")
  public void addEqRuleEffectiveFrom(
      int priority, String field, String value, String targetGroupId, String from) {
    addLeafRule(
        priority,
        field,
        Operator.EQ,
        value,
        targetGroupId,
        new EffectivePeriod(LocalDate.parse(from), null));
  }

  @Given(
      "the config has a rule with priority {int} matching {string} EQ {string} routing to {string} effective until {string}")
  public void addEqRuleEffectiveUntil(
      int priority, String field, String value, String targetGroupId, String to) {
    addLeafRule(
        priority,
        field,
        Operator.EQ,
        value,
        targetGroupId,
        new EffectivePeriod(null, LocalDate.parse(to)));
  }

  @Given(
      "the config has a rule with priority {int} matching {string} EQ {string} routing to {string} effective from {string} until {string}")
  public void addEqRuleEffectiveBetween(
      int priority, String field, String value, String targetGroupId, String from, String to) {
    addLeafRule(
        priority,
        field,
        Operator.EQ,
        value,
        targetGroupId,
        new EffectivePeriod(LocalDate.parse(from), LocalDate.parse(to)));
  }

  private IRouteWorkItemUseCase routingService() {
    return new RoutingService(routingConfigRepo, auditRepo, new SimpleMeterRegistry(), clock);
  }

  @Given(
      "the config has an inactive rule with priority {int} matching {string} EQ {string} routing to {string}")
  public void addInactiveSimpleEqRule(
      int priority, String field, String value, String targetGroupId) {
    pendingRules.add(
        new RoutingRule(
            "rule-" + (++ruleCounter),
            "rule-" + ruleCounter,
            priority,
            new LeafCondition(field, Operator.EQ, value),
            targetGroupId,
            false));
  }

  @Given("the config has a rule with priority {int} routing to {string} matching:")
  public void addComplexRule(int priority, String targetGroupId, String conditionJson)
      throws Exception {
    ConditionNode condition = parseConditionNode(objectMapper.readTree(conditionJson));
    pendingRules.add(
        new RoutingRule(
            "rule-" + (++ruleCounter),
            "rule-" + ruleCounter,
            priority,
            condition,
            targetGroupId,
            true));
  }

  // ── When ────────────────────────────────────────────────────────────────

  @When("a work item is routed with fields:")
  public void aWorkItemIsRoutedWithFields(DataTable dataTable) {
    // Build and save the routing config from accumulated rules
    List<RoutingRule> sortedRules =
        pendingRules.stream().sorted(Comparator.comparingInt(RoutingRule::priority)).toList();

    routingConfigRepo.save(
        new RoutingConfig("config-1", tenantId, workflowType, defaultGroupId, false, sortedRules));

    // Build nested fields map from the data table (dot-notation keys → values)
    Map<String, Object> fields = buildNestedMap(dataTable.asMap());

    result =
        routingService()
            .route(new WorkItemToRoute("work-item-001", tenantId, workflowType, "system", fields));
  }

  @When("a work item is routed with a non-numeric value for {string} of {string}")
  public void aWorkItemIsRoutedWithANonNumericValue(String field, String value) {
    List<RoutingRule> sortedRules =
        pendingRules.stream().sorted(Comparator.comparingInt(RoutingRule::priority)).toList();

    routingConfigRepo.save(
        new RoutingConfig("config-1", tenantId, workflowType, defaultGroupId, false, sortedRules));

    Map<String, Object> fields = buildNestedMap(Map.of(field, value));

    try {
      result =
          routingService()
              .route(
                  new WorkItemToRoute("work-item-001", tenantId, workflowType, "system", fields));
    } catch (IllegalArgumentException e) {
      routingFailure = e;
    }
  }

  // ── Then ────────────────────────────────────────────────────────────────

  @Then("routing fails because the field value is not numeric")
  public void routingFailsBecauseTheFieldValueIsNotNumeric() {
    assertThat(routingFailure)
        .as("expected a numeric-comparison failure")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Numeric operator applied to non-numeric values");
  }

  @Then("the failure names the field {string} but not the value {string}")
  public void theFailureNamesTheFieldButNotTheValue(String field, String value) {
    // Field values can be personal or financial data, and exception messages end up in logs.
    assertThat(routingFailure.getMessage())
        .as("failure message")
        .contains(field)
        .doesNotContain(value);
  }

  @Then("the work item is assigned to {string}")
  public void theWorkItemIsAssignedTo(String expectedGroupId) {
    assertThat(result.assignedGroupId()).as("assigned group").isEqualTo(expectedGroupId);
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
    assertThat(auditRepo.all()).as("audit entries").anyMatch(e -> e.eventType() == expectedType);
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

  /** Parses a JSON condition node (LEAF or GROUP) into the ConditionNode sealed hierarchy. */
  private ConditionNode parseConditionNode(JsonNode node) {
    String type = node.get("type").asString();
    return switch (type) {
      case "LEAF" ->
          new LeafCondition(
              node.get("field").asString(),
              Operator.valueOf(node.get("operator").asString()),
              parseLeafValue(node.get("value")));
      case "GROUP" ->
          new GroupCondition(
              LogicalOperator.valueOf(node.get("logicalOperator").asString()),
              StreamSupport.stream(node.get("children").spliterator(), false)
                  .map(this::parseConditionNode)
                  .toList());
      default -> throw new IllegalArgumentException("Unknown condition node type: " + type);
    };
  }

  private static Object parseLeafValue(JsonNode valueNode) {
    if (valueNode == null || valueNode.isNull()) {
      return null;
    }
    if (valueNode.isArray()) {
      return StreamSupport.stream(valueNode.spliterator(), false).map(JsonNode::asString).toList();
    }
    return valueNode.asString();
  }
}
