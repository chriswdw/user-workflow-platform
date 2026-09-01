# Prompt 04 — platform-workflow

## Goal
Implement the `platform-workflow` module: workflow state machine model, transition validation, `WorkflowService`, in-memory test doubles, and Cucumber BDD tests. **No Spring, no database.** Uses Micrometer for transition timing.

## Package root
`com.platform.workflow`

## Production files

### `domain/model/WorkflowState.java`
```java
public record WorkflowState(String name, boolean terminal, List<String> allowedRoles) {}
```

### `domain/model/TransitionTrigger.java`
Enum: `MANUAL`, `SYSTEM`

### `domain/model/TransitionActionType.java`
Enum: `REASSIGN_GROUP`, `NOTIFY`, `WEBHOOK`, `CREATE_CHILD_WORKFLOW`

### `domain/model/TransitionAction.java`
```java
public record TransitionAction(TransitionActionType type, Map<String, String> config) {}
```

### `domain/model/ValidationRule.java`
```java
public record ValidationRule(String field, String operator, String value) {}
```
Supported operators: `EXISTS`, `EQ`, `NEQ`.

### `domain/model/OnFailure.java`
Enum: `REJECT`, `RETRY`, `IGNORE`

### `domain/model/WorkflowTransition.java`
```java
public record WorkflowTransition(
    String name, String fromState, String toState,
    TransitionTrigger trigger, String systemEventType,
    List<String> allowedRoles, boolean requiresMakerChecker,
    List<TransitionAction> actions, List<ValidationRule> validationRules
) {}
```

### `domain/model/WorkflowConfig.java`
```java
public record WorkflowConfig(
    String id, String tenantId, String workflowType,
    String initialState, List<WorkflowState> states,
    List<WorkflowTransition> transitions, boolean active
)
```
Compact constructor validates:
- `tenantId`, `workflowType`, `initialState`, `states`, `transitions` non-null
- `initialState` must be in `states`
- Every transition's `fromState` and `toState` must be in `states`
- Throw `IllegalArgumentException` with message identifying the violating transition

Helper methods:
```java
public Optional<WorkflowTransition> findTransition(String name)
public Optional<WorkflowState> findState(String name)
```

### `domain/model/TransitionCommand.java`
```java
public record TransitionCommand(
    String tenantId, String workItemId, String transitionName,
    String actorUserId, String actorRole, Map<String, Object> additionalFields
) {}
```

### `domain/exception/WorkItemNotFoundException.java`
RuntimeException: `"WorkItem not found: " + workItemId`

### `domain/exception/WorkflowConfigNotFoundException.java`
RuntimeException: `"No active workflow config for workflowType=" + workflowType + " tenantId=" + tenantId`

### `domain/exception/InvalidTransitionException.java`
RuntimeException with provided message.

### `domain/exception/ForbiddenTransitionException.java`
RuntimeException with provided message.

### `domain/exception/ValidationFailedException.java`
RuntimeException with provided message.

### `domain/ports/in/ITransitionWorkItemUseCase.java`
```java
public interface ITransitionWorkItemUseCase {
    WorkItem transition(TransitionCommand command);
}
```
Imports: `com.platform.domain.model.WorkItem`, `com.platform.workflow.domain.model.TransitionCommand`

### `domain/ports/out/IWorkItemRepository.java`
```java
public interface IWorkItemRepository {
    Optional<WorkItem> findById(String tenantId, String workItemId);
    WorkItem save(WorkItem workItem);
}
```

### `domain/ports/out/IWorkflowConfigRepository.java`
```java
public interface IWorkflowConfigRepository {
    Optional<WorkflowConfig> findActiveByTenantAndWorkflowType(String tenantId, String workflowType);
}
```

### `domain/service/WorkflowService.java`
Implements `ITransitionWorkItemUseCase`. Constructor:
```java
public WorkflowService(IWorkItemRepository workItemRepository,
                       IWorkflowConfigRepository workflowConfigRepository,
                       IAuditRepository auditRepository,
                       IDomainEventPublisher eventPublisher,
                       MeterRegistry meterRegistry)
```
All fields `final`. No `@Autowired`.

`transition(TransitionCommand command)` algorithm:
1. Start `Timer.Sample`
2. Load work item (throw `WorkItemNotFoundException` if absent)
3. Load active workflow config (throw `WorkflowConfigNotFoundException` if absent)
4. Find transition by name (throw `InvalidTransitionException` if unknown)
5. Validate `transition.fromState()` equals `workItem.status()` (throw `InvalidTransitionException`)
6. Validate actor role is in `transition.allowedRoles()` (throw `ForbiddenTransitionException`)
7. Evaluate `validationRules` — `EXISTS`/`EQ`/`NEQ` checked via `FieldPathResolver.resolve()` — throw `ValidationFailedException` on first failure
8. Merge `command.additionalFields()` into a mutable copy of `workItem.fields()` using `setNestedValue()` helper (dot-notation path splitting); write FIELD_UPDATE audit entry
9. Execute `REASSIGN_GROUP` actions synchronously; write GROUP_REASSIGNMENT audit entry
10. Apply status transition via `withStatus(transition.toState())` and `withMakerUserId(command.actorUserId())`
11. Write STATE_TRANSITION audit entry
12. Save updated work item; publish `DomainEvent` with eventType `"STATE_TRANSITION"` and payload `{workflowType, transition, previousState, newState, actorUserId}`
13. Stop timer with tags `workflowType`, `transition`

Private helpers:
- `setNestedValue(Map<String,Object> map, String dotPath, Object value)` — recurse into nested maps, creating `HashMap` entries as needed
- `stateTransitionAuditEntry(...)` — `AuditEventType.STATE_TRANSITION`, idempotencyKey = `workItemId + ":" + transitionName`
- `groupReassignmentAuditEntry(...)` — `AuditEventType.GROUP_REASSIGNMENT`, idempotencyKey = `workItemId + ":" + transitionName + ":reassign"`
- `fieldUpdateAuditEntry(...)` — `AuditEventType.FIELD_UPDATE`, changedFields from before/after the merge
- `stateTransitionEvent(...)` — `DomainEvent` with `UUID.randomUUID()` eventId

## Test files

### `src/test/java/com/platform/workflow/doubles/InMemoryWorkItemRepository.java`
Implements `IWorkItemRepository`. Backed by `Map<String, WorkItem>` keyed by `id`. `save` just puts and returns. Expose `getAll()`.

### `src/test/java/com/platform/workflow/doubles/InMemoryWorkflowConfigRepository.java`
Implements `IWorkflowConfigRepository`. Backed by `Map<String, WorkflowConfig>`. Expose `put(config)`.

### `src/test/java/com/platform/workflow/doubles/InMemoryWorkflowAuditRepository.java`
Implements `IAuditRepository`. Backed by `List<AuditEntry>`. Expose `getAll()`.

### No-op domain event publisher
Use an anonymous lambda `event -> {}` in tests — no separate class needed.

### `src/test/java/com/platform/workflow/CucumberSuiteTest.java`
```java
@Suite @IncludeEngines("cucumber")
@SelectClasspathResource("features/workflow")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.platform.workflow.steps")
public class CucumberSuiteTest {}
```

### `src/test/resources/features/workflow/workflow.feature`
Cover:
- Successful state transition by authorised role
- InvalidTransitionException when wrong fromState
- ForbiddenTransitionException when role not allowed
- ValidationFailedException when required field missing
- REASSIGN_GROUP action executed correctly
- Audit entry written for each transition

### `src/test/java/com/platform/workflow/steps/WorkflowStepDefinitions.java`
Spring-free. Build `WorkflowService` with in-memory doubles and `SimpleMeterRegistry`. Build minimal `WorkflowConfig` (single state `NEW` → `IN_REVIEW` transition named `assign`) programmatically. Store the result or caught exception. Assert in `@Then` steps.

### `src/test/java/com/platform/workflow/domain/service/WorkflowServiceTest.java`
JUnit 5 unit tests covering the same scenarios as the feature file, plus:
- `additionalFieldsMergedCorrectly()` — verifies dot-notation merge
- `optimisticLockingPassThrough()` — save throws, exception propagates

## Constraints
- `FieldPathResolver` imported from `com.platform.domain.shared.FieldPathResolver`
- No Spring context in any test class
- Timer tag values must be non-null (use `"unknown"` fallback for null workflowType)
- `BigDecimal` not needed here (validation rules only support EXISTS/EQ/NEQ)

## Verification
```bash
./gradlew :platform-workflow:cucumber
./gradlew :platform-workflow:test
```
