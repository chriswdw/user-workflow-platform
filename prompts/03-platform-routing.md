# Prompt 03 — platform-routing

## Goal
Implement the `platform-routing` module: condition tree model, routing config, routing service with condition evaluator, in-memory test doubles, and a Cucumber BDD test for routing logic. **No Spring, no database.** Pure domain + Micrometer for metrics.

## Package root
`com.platform.routing`

## Production files

### `domain/model/ConditionNode.java`
```java
public sealed interface ConditionNode permits LeafCondition, GroupCondition {}
```

### `domain/model/LeafCondition.java`
```java
public record LeafCondition(String field, Operator operator, Object value) implements ConditionNode {}
```

### `domain/model/GroupCondition.java`
```java
public record GroupCondition(LogicalOperator logicalOperator, List<ConditionNode> children) implements ConditionNode {}
```

### `domain/model/Operator.java`
Enum: `EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `IN`, `NOT_IN`, `CONTAINS`, `REGEX`, `EXISTS`

### `domain/model/LogicalOperator.java`
Enum: `AND`, `OR`

### `domain/model/RoutingRule.java`
```java
public record RoutingRule(
    String id, String name, int priority,
    ConditionNode conditions, String targetGroupId, boolean active
) {}
```

### `domain/model/RoutingConfig.java`
```java
public record RoutingConfig(
    String id, String tenantId, String workflowType,
    String defaultGroupId, boolean alertOnDefault, List<RoutingRule> rules
)
```
Compact constructor: require non-null `tenantId`, `workflowType`, `defaultGroupId`, `rules`.

### `domain/model/WorkItemToRoute.java`
```java
public record WorkItemToRoute(
    String id, String tenantId, String workflowType, Map<String, Object> fields
) {}
```

### `domain/model/RoutingResult.java`
```java
public record RoutingResult(
    String assignedGroupId, boolean routedByDefault, String matchedRuleId
) {}
```

### `domain/exception/RoutingConfigNotFoundException.java`
`RuntimeException` with message `"No routing config found for workflowType=" + workflowType + " tenantId=" + tenantId`.

### `domain/ports/in/IRouteWorkItemUseCase.java`
```java
public interface IRouteWorkItemUseCase {
    RoutingResult route(WorkItemToRoute workItem);
}
```

### `domain/ports/out/IRoutingConfigRepository.java`
```java
public interface IRoutingConfigRepository {
    Optional<RoutingConfig> findByTenantAndWorkflowType(String tenantId, String workflowType);
}
```

### `domain/service/ConditionEvaluator.java`
Package-private final class, private constructor. Static method:
```java
static boolean evaluate(ConditionNode node, Map<String, Object> fields)
```
- Null node → return true
- `LeafCondition`: if operator is `EXISTS`, check `FieldPathResolver.resolve(fields, leaf.field()).isPresent()`; otherwise resolve field, return false if empty, then switch on operator:
  - `EQ`/`NEQ`: `toString()` equality
  - `GT`/`GTE`/`LT`/`LTE`: `BigDecimal` comparison (throw `IllegalArgumentException` on non-numeric)
  - `IN`/`NOT_IN`: convert comparand to `List<String>` via collection or single value, check `contains`
  - `CONTAINS`: `fieldValue.toString().contains(comparand.toString())`
  - `REGEX`: `Pattern.matches(comparand.toString(), fieldValue.toString())`
- `GroupCondition`: `AND` → `allMatch`, `OR` → `anyMatch`

Uses `FieldPathResolver` from `platform-domain` (import `com.platform.domain.shared.FieldPathResolver`).

### `domain/service/RoutingService.java`
Implements `IRouteWorkItemUseCase`. Constructor: `(IRoutingConfigRepository repo, IAuditRepository auditRepository, MeterRegistry meterRegistry)`. The `IAuditRepository` parameter is `com.platform.domain.ports.out.IAuditRepository`.

`route(WorkItemToRoute workItem)`:
1. Load config (throw `RoutingConfigNotFoundException` if missing)
2. Sort active rules by `priority` ascending; evaluate each with `ConditionEvaluator.evaluate(rule.conditions(), workItem.fields())` — first match wins
3. If no match: return `new RoutingResult(config.defaultGroupId(), true, null)`
4. On match: return `new RoutingResult(matchedRule.targetGroupId(), false, matchedRule.id())`
5. Record Micrometer counter `routing.result` with tags `workflowType`, `routedByDefault`

## Test files

### `src/test/java/com/platform/routing/doubles/InMemoryRoutingConfigRepository.java`
Implements `IRoutingConfigRepository`. Backed by `Map<String, RoutingConfig>` keyed on `tenantId + ":" + workflowType`.

### `src/test/java/com/platform/routing/doubles/InMemoryAuditRepository.java`
Implements `IAuditRepository`. Backed by `List<AuditEntry>`. Expose `getAll()` for assertions.

### `src/test/java/com/platform/routing/CucumberSuiteTest.java`
```java
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/routing")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.platform.routing.steps")
public class CucumberSuiteTest {}
```

### `src/test/resources/features/routing/routing.feature`
```gherkin
Feature: Work item routing

  Scenario: Route to matched group by rule
    Given a routing config with a rule matching workflowType "SETTLEMENT_EXCEPTION" field "trade.region" EQ "EMEA" targeting group "group-emea"
    And the default group is "group-ops"
    When a work item arrives with field "trade.region" = "EMEA"
    Then the work item is assigned to group "group-emea"
    And it was not routed by default

  Scenario: Fall back to default group when no rule matches
    Given a routing config with a rule matching workflowType "SETTLEMENT_EXCEPTION" field "trade.region" EQ "EMEA" targeting group "group-emea"
    And the default group is "group-ops"
    When a work item arrives with field "trade.region" = "APAC"
    Then the work item is assigned to group "group-ops"
    And it was routed by default

  Scenario: AND group condition routes correctly
    Given a routing config with an AND rule: field "trade.region" EQ "EMEA" AND field "trade.notionalAmount.amount" GT "1000000" targeting "group-large-emea"
    And the default group is "group-ops"
    When a work item arrives with field "trade.region" = "EMEA" and "trade.notionalAmount.amount" = "2000000"
    Then the work item is assigned to group "group-large-emea"
```

### `src/test/java/com/platform/routing/steps/RoutingStepDefinitions.java`
Spring-free step defs. Use `InMemoryRoutingConfigRepository`, `InMemoryAuditRepository`, and `io.micrometer.core.instrument.simple.SimpleMeterRegistry`. Wire `RoutingService` in a `@Before` hook. Build `RoutingConfig` and `WorkItemToRoute` objects programmatically from step parameters. Store `RoutingResult` from `route()`. Assert in `@Then` steps.

### `src/test/java/com/platform/routing/domain/service/RoutingServiceTest.java`
JUnit 5 unit tests (no Cucumber):
- `routesToMatchedGroup()`
- `routesToDefaultGroupWhenNoRuleMatches()`
- `skipsInactiveRules()`
- `evaluatesAndGroupCondition()`
- `throwsWhenConfigNotFound()`

## Constraints
- `ConditionEvaluator` is package-private in `domain/service/`
- `RoutingService` depends on `IAuditRepository` and `IRoutingConfigRepository` — never on concrete classes
- `BigDecimal` for all GT/GTE/LT/LTE comparisons
- No `@Autowired`, no Spring context in any test

## Verification
```bash
./gradlew :platform-routing:test    # All tests pass
./gradlew :platform-routing:cucumber  # Cucumber scenarios pass
```
