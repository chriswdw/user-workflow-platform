package com.platform.api.adapter.out.postgres;

import com.platform.config.domain.model.ConfigType;
import com.platform.routing.domain.model.ConditionNode;
import com.platform.routing.domain.model.EffectivePeriod;
import com.platform.routing.domain.model.GroupCondition;
import com.platform.routing.domain.model.LeafCondition;
import com.platform.routing.domain.model.LogicalOperator;
import com.platform.routing.domain.model.Operator;
import com.platform.routing.domain.model.RoutingConfig;
import com.platform.routing.domain.model.RoutingRule;
import com.platform.routing.domain.ports.out.IRoutingConfigRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the active routing configuration for a tenant and workflow type from {@code
 * config_documents} (config type {@code ROUTING_CONFIG}) and maps the JSON document, which follows
 * {@code schemas/config/routing-config.schema.json}, onto the routing domain model.
 *
 * <p>The lookup is served by the {@code (tenant_id, workflow_type, config_type, active)} index.
 *
 * <p>Routing decides where work goes, so a config that cannot be read exactly is an error, never a
 * guess: a malformed document, or more than one active document for the same tenant and workflow
 * type, raises {@link IllegalStateException} naming the document.
 */
public class RoutingConfigJdbcRepository implements IRoutingConfigRepository {

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public RoutingConfigJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<RoutingConfig> findActiveByTenantAndWorkflowType(
      String tenantId, String workflowType) {
    String sql =
        """
        SELECT id, content FROM config_documents
        WHERE tenant_id = :tenantId
          AND workflow_type = :workflowType
          AND config_type = :configType
          AND active = true
        """;
    var params =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("workflowType", workflowType)
            .addValue("configType", ConfigType.ROUTING_CONFIG.name());

    List<ConfigRow> rows =
        jdbc.query(
            sql, params, (rs, n) -> new ConfigRow(rs.getString("id"), rs.getString("content")));

    if (rows.size() > 1) {
      throw new IllegalStateException(
          "There is more than one active ROUTING_CONFIG for workflowType="
              + workflowType
              + ", tenantId="
              + tenantId
              + "; refusing to pick one");
    }
    return rows.stream().findFirst().map(row -> parse(row.id(), row.json()));
  }

  // ── Mapping ───────────────────────────────────────────────────────────────

  private RoutingConfig parse(String documentId, String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      List<RoutingRule> rules = new ArrayList<>();
      for (JsonNode rule : root.path("rules")) {
        rules.add(parseRule(rule));
      }
      return new RoutingConfig(
          text(root, "id"),
          requiredText(root, "tenantId"),
          requiredText(root, "workflowType"),
          requiredText(root, "defaultGroup"),
          root.path("alertOnDefault").asBoolean(false),
          List.copyOf(rules));
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Invalid ROUTING_CONFIG document '" + documentId + "': " + e.getMessage(), e);
    }
  }

  private RoutingRule parseRule(JsonNode rule) {
    return new RoutingRule(
        requiredText(rule, "id"),
        requiredText(rule, "name"),
        requiredInt(rule, "priority"),
        parseCondition(rule.path("conditions")),
        requiredText(rule, "targetGroup"),
        requiredBoolean(rule, "active"),
        new EffectivePeriod(date(rule, "effectiveFrom"), date(rule, "effectiveTo")));
  }

  private ConditionNode parseCondition(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) {
      throw new IllegalStateException("a rule has no 'conditions'");
    }
    String type = requiredText(node, "type");
    return switch (type) {
      case "LEAF" ->
          new LeafCondition(
              requiredText(node, "field"),
              Operator.valueOf(requiredText(node, "operator")),
              comparand(node.get("value")));
      case "GROUP" ->
          new GroupCondition(
              LogicalOperator.valueOf(requiredText(node, "logicalOperator")), children(node));
      default -> throw new IllegalStateException("unknown condition node type '" + type + "'");
    };
  }

  private List<ConditionNode> children(JsonNode group) {
    List<ConditionNode> children = new ArrayList<>();
    for (JsonNode child : group.path("children")) {
      children.add(parseCondition(child));
    }
    return List.copyOf(children);
  }

  /**
   * Comparands are kept as text (or a list of text for IN / NOT_IN), matching how the evaluator
   * compares them. Numbers are written without exponent so monetary amounts survive intact.
   */
  private static Object comparand(JsonNode value) {
    if (value == null || value.isNull() || value.isMissingNode()) {
      return null;
    }
    if (value.isArray()) {
      List<String> items = new ArrayList<>();
      for (JsonNode item : value) {
        items.add(scalarText(item));
      }
      return List.copyOf(items);
    }
    return scalarText(value);
  }

  private static String scalarText(JsonNode node) {
    return node.isNumber() ? node.decimalValue().toPlainString() : node.asString();
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asString();
  }

  private static String requiredText(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("missing required field '" + field + "'");
    }
    return value;
  }

  private static int requiredInt(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isNumber()) {
      throw new IllegalStateException("missing or non-numeric required field '" + field + "'");
    }
    return value.asInt();
  }

  private static boolean requiredBoolean(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isBoolean()) {
      throw new IllegalStateException("missing or non-boolean required field '" + field + "'");
    }
    return value.asBoolean();
  }

  private static LocalDate date(JsonNode node, String field) {
    String value = text(node, field);
    return value == null ? null : LocalDate.parse(value);
  }

  private record ConfigRow(String id, String json) {}
}
