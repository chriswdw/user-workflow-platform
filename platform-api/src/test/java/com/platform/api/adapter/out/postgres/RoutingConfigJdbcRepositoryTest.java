package com.platform.api.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.routing.domain.model.EffectivePeriod;
import com.platform.routing.domain.model.GroupCondition;
import com.platform.routing.domain.model.LeafCondition;
import com.platform.routing.domain.model.LogicalOperator;
import com.platform.routing.domain.model.Operator;
import com.platform.routing.domain.model.RoutingConfig;
import com.platform.routing.domain.model.RoutingRule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RoutingConfigJdbcRepositoryTest {

  private static final String TENANT = "tenant-1";
  private static final String WORKFLOW = "SETTLEMENT_EXCEPTION";

  private static final NamedParameterJdbcTemplate jdbc =
      new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
  private static final ObjectMapper objectMapper = new JsonMapper();

  private final RoutingConfigJdbcRepository repository =
      new RoutingConfigJdbcRepository(jdbc, objectMapper);

  @BeforeEach
  void truncate() {
    jdbc.update("TRUNCATE config_documents CASCADE", Map.of());
  }

  // ── Lookup ────────────────────────────────────────────────────────────────

  @Test
  void returnsEmptyWhenNoRoutingConfigExists() {
    assertThat(repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW)).isEmpty();
  }

  @Test
  void doesNotReturnAnInactiveConfig() {
    insert("rc-1", TENANT, WORKFLOW, "ROUTING_CONFIG", false, config("rc-1", TENANT, WORKFLOW));

    assertThat(repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW)).isEmpty();
  }

  @Test
  void doesNotCrossTenantBoundary() {
    insert(
        "rc-1", "tenant-A", WORKFLOW, "ROUTING_CONFIG", true, config("rc-1", "tenant-A", WORKFLOW));

    assertThat(repository.findActiveByTenantAndWorkflowType("tenant-B", WORKFLOW)).isEmpty();
  }

  @Test
  void doesNotCrossWorkflowTypeBoundary() {
    insert(
        "rc-1",
        TENANT,
        "TRADE_BREAK",
        "ROUTING_CONFIG",
        true,
        config("rc-1", TENANT, "TRADE_BREAK"));

    assertThat(repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW)).isEmpty();
  }

  @Test
  void ignoresOtherConfigTypesForTheSameTenantAndWorkflow() {
    insert(
        "ing-1",
        TENANT,
        WORKFLOW,
        "INGESTION_SOURCE_CONFIG",
        true,
        config("ing-1", TENANT, WORKFLOW));

    assertThat(repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW)).isEmpty();
  }

  @Test
  void refusesToGuessWhenMoreThanOneActiveConfigExists() {
    insert("rc-1", TENANT, WORKFLOW, "ROUTING_CONFIG", true, config("rc-1", TENANT, WORKFLOW));
    insert("rc-2", TENANT, WORKFLOW, "ROUTING_CONFIG", true, config("rc-2", TENANT, WORKFLOW));

    assertThatThrownBy(() -> repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("more than one active ROUTING_CONFIG");
  }

  // ── Mapping ───────────────────────────────────────────────────────────────

  @Test
  void mapsTheConfigAndItsRules() {
    Map<String, Object> content = config("rc-1", TENANT, WORKFLOW);
    content.put("alertOnDefault", true);
    content.put("rules", List.of(seniorRule(), flaggedRule()));
    insert("rc-1", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    RoutingConfig result = repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW).get();

    assertThat(result.id()).isEqualTo("rc-1");
    assertThat(result.tenantId()).isEqualTo(TENANT);
    assertThat(result.workflowType()).isEqualTo(WORKFLOW);
    assertThat(result.defaultGroupId()).isEqualTo("group-ops");
    assertThat(result.alertOnDefault()).isTrue();
    assertThat(result.rules()).hasSize(2);
  }

  @Test
  void mapsRuleFieldsIncludingTargetGroupPriorityAndEffectivePeriod() {
    Map<String, Object> content = config("rc-1", TENANT, WORKFLOW);
    content.put("rules", List.of(seniorRule()));
    insert("rc-1", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    RoutingRule rule =
        repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW).get().rules().get(0);

    assertThat(rule.id()).isEqualTo("r-senior");
    assertThat(rule.name()).isEqualTo("Senior");
    assertThat(rule.priority()).isEqualTo(2);
    assertThat(rule.targetGroupId()).isEqualTo("group-senior");
    assertThat(rule.active()).isTrue();
    assertThat(rule.effective())
        .isEqualTo(new EffectivePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
  }

  @Test
  void mapsANestedConditionTree() {
    Map<String, Object> content = config("rc-1", TENANT, WORKFLOW);
    content.put("rules", List.of(seniorRule()));
    insert("rc-1", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    RoutingRule rule =
        repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW).get().rules().get(0);

    assertThat(rule.conditions())
        .isEqualTo(
            new GroupCondition(
                LogicalOperator.AND,
                List.of(
                    new LeafCondition("trade.notionalAmount.amount", Operator.GTE, "10000000"),
                    new LeafCondition(
                        "counterparty.region", Operator.IN, List.of("EMEA", "APAC")))));
  }

  @Test
  void aRuleWithNoDatesIsUnboundedAndAnInactiveFlagIsPreserved() {
    Map<String, Object> content = config("rc-1", TENANT, WORKFLOW);
    content.put("rules", List.of(flaggedRule()));
    insert("rc-1", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    RoutingRule rule =
        repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW).get().rules().get(0);

    assertThat(rule.active()).isFalse();
    assertThat(rule.effective()).isEqualTo(EffectivePeriod.unbounded());
    assertThat(rule.conditions())
        .isEqualTo(new LeafCondition("flags.regulatoryHold", Operator.EXISTS, null));
  }

  @Test
  void mapsEveryOperatorAndKeepsNumericComparandsAsPlainText() {
    List<Map<String, Object>> rules = new ArrayList<>();
    int priority = 1;
    for (Operator operator : Operator.values()) {
      rules.add(rule("r-" + operator, priority++, leaf("f." + operator, operator.name(), "x")));
    }
    rules.add(rule("r-number", priority++, leaf("trade.notionalAmount.amount", "GT", 1000000.50)));
    rules.add(rule("r-integer", priority, leaf("trade.quantity", "LT", 250)));
    Map<String, Object> content = config("rc-1", TENANT, WORKFLOW);
    content.put("rules", rules);
    insert("rc-1", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    List<RoutingRule> result =
        repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW).get().rules();

    for (Operator operator : Operator.values()) {
      assertThat(result)
          .anySatisfy(r ->
              assertThat(r.conditions())
                  .isEqualTo(new LeafCondition("f." + operator, operator, "x")));
    }
    assertThat(result)
        .anySatisfy(r ->
            assertThat(r.conditions())
                .isEqualTo(
                    new LeafCondition("trade.notionalAmount.amount", Operator.GT, "1000000.5")));
    assertThat(result)
        .anySatisfy(r ->
            assertThat(r.conditions())
                .isEqualTo(new LeafCondition("trade.quantity", Operator.LT, "250")));
  }

  // ── Malformed config is surfaced, never guessed ───────────────────────────

  @Test
  void anUnknownOperatorIsReportedAgainstTheConfig() {
    Map<String, Object> content = config("rc-bad", TENANT, WORKFLOW);
    content.put("rules", List.of(rule("r-1", 1, leaf("a.b", "SOUNDS_LIKE", "x"))));
    insert("rc-bad", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    assertThat(failureOf()).hasMessageContaining("rc-bad").hasMessageContaining("ROUTING_CONFIG");
  }

  @Test
  void anUnknownConditionNodeTypeIsReported() {
    Map<String, Object> content = config("rc-bad", TENANT, WORKFLOW);
    content.put("rules", List.of(rule("r-1", 1, Map.of("type", "BLOB"))));
    insert("rc-bad", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    assertThat(failureOf()).hasMessageContaining("rc-bad");
  }

  @Test
  void aMissingDefaultGroupIsReported() {
    Map<String, Object> content = config("rc-bad", TENANT, WORKFLOW);
    content.remove("defaultGroup");
    insert("rc-bad", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    assertThat(failureOf()).hasMessageContaining("rc-bad").hasMessageContaining("defaultGroup");
  }

  @Test
  void aRuleWhoseEndDatePrecedesItsStartDateIsReported() {
    Map<String, Object> bad = rule("r-1", 1, leaf("a.b", "EQ", "x"));
    bad.put("effectiveFrom", "2026-06-30");
    bad.put("effectiveTo", "2026-06-01");
    Map<String, Object> content = config("rc-bad", TENANT, WORKFLOW);
    content.put("rules", List.of(bad));
    insert("rc-bad", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    assertThat(failureOf()).hasMessageContaining("rc-bad");
  }

  @Test
  void aRuleWithNoConditionsIsReported() {
    Map<String, Object> bad = rule("r-1", 1, leaf("a.b", "EQ", "x"));
    bad.remove("conditions");
    Map<String, Object> content = config("rc-bad", TENANT, WORKFLOW);
    content.put("rules", List.of(bad));
    insert("rc-bad", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    assertThat(failureOf()).hasMessageContaining("rc-bad").hasMessageContaining("conditions");
  }

  @Test
  void aNonNumericPriorityIsReported() {
    Map<String, Object> bad = rule("r-1", 1, leaf("a.b", "EQ", "x"));
    bad.put("priority", "high");
    Map<String, Object> content = config("rc-bad", TENANT, WORKFLOW);
    content.put("rules", List.of(bad));
    insert("rc-bad", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    assertThat(failureOf()).hasMessageContaining("rc-bad").hasMessageContaining("priority");
  }

  @Test
  void aRuleWithoutAnActiveFlagIsReportedNotAssumedActive() {
    Map<String, Object> bad = rule("r-1", 1, leaf("a.b", "EQ", "x"));
    bad.remove("active");
    Map<String, Object> content = config("rc-bad", TENANT, WORKFLOW);
    content.put("rules", List.of(bad));
    insert("rc-bad", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    assertThat(failureOf()).hasMessageContaining("rc-bad").hasMessageContaining("active");
  }

  @Test
  void aMalformedDateIsReported() {
    Map<String, Object> bad = rule("r-1", 1, leaf("a.b", "EQ", "x"));
    bad.put("effectiveFrom", "next tuesday");
    Map<String, Object> content = config("rc-bad", TENANT, WORKFLOW);
    content.put("rules", List.of(bad));
    insert("rc-bad", TENANT, WORKFLOW, "ROUTING_CONFIG", true, content);

    assertThat(failureOf()).hasMessageContaining("rc-bad");
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private Throwable failureOf() {
    return org.assertj.core.api.Assertions.catchThrowable(
        () -> repository.findActiveByTenantAndWorkflowType(TENANT, WORKFLOW));
  }

  private static Map<String, Object> config(String id, String tenant, String workflow) {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("id", id);
    content.put("tenantId", tenant);
    content.put("workflowType", workflow);
    content.put("defaultGroup", "group-ops");
    content.put("alertOnDefault", false);
    content.put("rules", List.of());
    return content;
  }

  private static Map<String, Object> seniorRule() {
    Map<String, Object> rule =
        rule(
            "r-senior",
            2,
            Map.of(
                "type",
                "GROUP",
                "logicalOperator",
                "AND",
                "children",
                List.of(
                    leaf("trade.notionalAmount.amount", "GTE", "10000000"),
                    leaf("counterparty.region", "IN", List.of("EMEA", "APAC")))));
    rule.put("name", "Senior");
    rule.put("targetGroup", "group-senior");
    rule.put("effectiveFrom", "2026-01-01");
    rule.put("effectiveTo", "2026-12-31");
    return rule;
  }

  private static Map<String, Object> flaggedRule() {
    Map<String, Object> rule = rule("r-flagged", 1, leaf("flags.regulatoryHold", "EXISTS", null));
    rule.put("active", false);
    rule.put("targetGroup", "group-compliance");
    return rule;
  }

  private static Map<String, Object> rule(String id, int priority, Object conditions) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("id", id);
    rule.put("name", id);
    rule.put("priority", priority);
    rule.put("conditions", conditions);
    rule.put("targetGroup", "group-x");
    rule.put("active", true);
    return rule;
  }

  private static Map<String, Object> leaf(String field, String operator, Object value) {
    Map<String, Object> leaf = new LinkedHashMap<>();
    leaf.put("type", "LEAF");
    leaf.put("field", field);
    leaf.put("operator", operator);
    leaf.put("value", value);
    return leaf;
  }

  private static void insert(
      String id,
      String tenant,
      String workflow,
      String configType,
      boolean active,
      Map<String, Object> content) {
    jdbc.update(
        """
        INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
        VALUES (:id, :tenant, :workflow, :configType, CAST(:content AS jsonb), '1', :active)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenant", tenant)
            .addValue("workflow", workflow)
            .addValue("configType", configType)
            .addValue("content", toJson(content))
            .addValue("active", active));
  }

  private static String toJson(Map<String, Object> content) {
    try {
      return objectMapper.writeValueAsString(content);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
