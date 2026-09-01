/# Observability: structured logging and metrics for submission lifecycle

## Context
`WorkflowTypeSubmissionService`, `WorkflowTypeSubmissionController`, and
`WorkflowTypeSubmissionJdbcRepository` contain no logging, no metrics, and no MDC enrichment.
CLAUDE.md requires all three as part of every feature's Definition of Done:

> Metrics, trace propagation, and structured logging implemented.
> MDC must always contain `correlationId`, `workItemId`, `userId`, `tenantId`.

The `MdcFilter` in `platform-observability` already places `userId`, `tenantId`, and `role` into
MDC for every HTTP request — no change needed there. The work here is:

1. Add SLF4J logging to the domain service and the JDBC repository.
2. Enrich MDC with `submissionId` in the REST controller for the duration of each request.
3. Add a Micrometer counter for submission lifecycle events in the controller (the only layer
   that may depend on Spring/Micrometer without violating hexagonal architecture).

**Logging pattern in this codebase** (see `WorkItemKafkaConsumer`):
```java
private static final Logger log = LoggerFactory.getLogger(WorkItemKafkaConsumer.class);
log.info("workItemId={} tenantId={} workflowType={} msg=ingested", ...);
```
Key=value inline format, no MDC.put in the domain service (MdcFilter owns userId/tenantId).

## Impacted Files

| File | Change |
|------|--------|
| `platform-config-engine/src/main/java/com/platform/config/domain/service/WorkflowTypeSubmissionService.java` | Add SLF4J logger; INFO log at each state-changing method |
| `platform-api/src/main/java/com/platform/api/adapter/in/rest/WorkflowTypeSubmissionController.java` | Add SLF4J logger; MDC.put("submissionId") per mutating endpoint; Micrometer counter |
| `platform-api/src/main/java/com/platform/api/adapter/out/postgres/WorkflowTypeSubmissionJdbcRepository.java` | Add SLF4J logger; DEBUG log on each query |

No new dependencies — SLF4J is already on the classpath via `spring-boot-starter-logging`;
Micrometer is already on the classpath via `spring-boot-starter-actuator`.

## Technical Requirements

### WorkflowTypeSubmissionService — SLF4J only (no Micrometer, no Spring)

Add at class level:
```java
private static final Logger log = LoggerFactory.getLogger(WorkflowTypeSubmissionService.class);
```

Log at **INFO** immediately before `repo.save(...)` returns for every state-changing method.
Log format: `submissionId={} tenantId={} workflowType={} actor={} msg=<action>`

| Method | msg value |
|--------|-----------|
| `create` (DRAFT path) | `submission_created` |
| `create` (auto-approve path, inside `autoApprove`) | `submission_auto_approved` |
| `saveDraft` | `draft_saved step={}` (include `currentStep`) |
| `submit` | `submission_submitted_for_approval` |
| `approve` | `submission_approved reviewer={}` |
| `reject` | `submission_rejected reviewer={}` |
| `revise` | `submission_revised` |

Log `SubmissionAlreadyExistsException` and `SubmissionNotFoundException` at **WARN**:
```java
log.warn("submissionId={} tenantId={} workflowType={} msg=duplicate_submission_rejected",
         command.workflowType(), command.tenantId(), command.workflowType());
```

### WorkflowTypeSubmissionController — SLF4J + MDC + Micrometer

Add at class level:
```java
private static final Logger log = LoggerFactory.getLogger(WorkflowTypeSubmissionController.class);
private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
```

Add `MeterRegistry meterRegistry` as the **last** constructor parameter and assign it.

**MDC enrichment** for all mutating endpoints (`create`, `saveDraft`, `submit`, `approve`,
`reject`, `revise`) — wrap the try block:
```java
MDC.put("submissionId", id);   // for endpoints that have a path variable
try {
    // existing body
} finally {
    MDC.remove("submissionId");
}
```
For `create` (no id yet), put `submissionId` after the result is returned:
```java
var result = createUseCase.create(...);
MDC.put("submissionId", result.id());
try {
    log.info("submissionId={} tenantId={} workflowType={} msg=submission_created",
             result.id(), auth.tenantId(), result.workflowType());
    meterRegistry.counter("submission.lifecycle",
            "action", "create", "tenant", auth.tenantId()).increment();
    return ResponseEntity.status(201).body(WorkflowTypeSubmissionResponse.from(result));
} finally {
    MDC.remove("submissionId");
}
```

**Counter name**: `submission.lifecycle`
**Tags**: `action` (create | save_draft | submit | approve | reject | revise), `tenant` (auth.tenantId())

Log at **INFO** on success. Log at **WARN** for 409/403/422 outcomes with the exception message.

Import needed: `org.slf4j.MDC`

### WorkflowTypeSubmissionJdbcRepository — SLF4J DEBUG

Add at class level:
```java
private static final Logger log = LoggerFactory.getLogger(WorkflowTypeSubmissionJdbcRepository.class);
```

In `findById`: `log.debug("submissionId={} tenantId={} msg=find_by_id", submissionId, tenantId);`
In `findByTenantAndStatus`: `log.debug("tenantId={} status={} msg=find_by_status", tenantId, status);`
In `findByTenantAndStatusAndUser`: `log.debug("tenantId={} userId={} status={} msg=find_by_status_user", tenantId, userId, status);`
In `save` (the OptimisticLockingFailureException branch): `log.warn("submissionId={} version={} msg=optimistic_lock_conflict", s.id(), s.version() - 1);`

## Step-by-Step

1. **WorkflowTypeSubmissionService.java**
   - Add `import org.slf4j.Logger;` and `import org.slf4j.LoggerFactory;`
   - Add logger field after the constants block (line 30 area)
   - In `create()`: add INFO log after the `if (!makerCheckerEnabled)` branch resolves, covering both DRAFT and auto-approve paths
   - In `saveDraft()`: add INFO log before the `return repo.save(...)` call, include `currentStep`
   - In `submit()`: add INFO log before the `return repo.save(...)` call
   - In `approve()`: add INFO log before the `return repo.save(...)` call
   - In `reject()`: add INFO log before the `return repo.save(...)` call
   - In `revise()`: add INFO log before the `return repo.save(...)` call
   - In `autoApprove()`: add INFO log before the `return repo.save(...)` call
   - In `create()` `SubmissionAlreadyExistsException` throw site: add WARN log before throwing

2. **WorkflowTypeSubmissionController.java**
   - Add `import io.micrometer.core.instrument.MeterRegistry;`
   - Add `import org.slf4j.Logger;`, `import org.slf4j.LoggerFactory;`, `import org.slf4j.MDC;`
   - Add `private final MeterRegistry meterRegistry;` field
   - Add `MeterRegistry meterRegistry` to constructor parameters and assignment
   - Wrap each mutating method body with MDC.put/remove and add INFO log + counter increment on success
   - Add WARN logs on error branches (409, 403, 422) that include `e.getMessage()`

3. **WorkflowTypeSubmissionJdbcRepository.java**
   - Add Logger field
   - Add DEBUG log entry at the start of each query method
   - Add WARN log in the `rows == 0` branch of `save()`

4. **Update wiring** — `PostgresAdapterConfig.workflowTypeSubmissionService(...)` bean does not need changes (MeterRegistry is only in the controller layer). `WorkflowTypeSubmissionController` is autowired by Spring Boot and `MeterRegistry` is already a Spring bean — it will be injected automatically once added to the constructor.

5. **Run** `./gradlew build cucumber` — must pass.

## Test Plan
- `./gradlew build cucumber` green is sufficient for this change — no new test required.
- Logging is not unit-tested per project conventions; metrics counters are infrastructure verified by the Spring context starting correctly.
- **Manual smoke test** (optional): run the app locally, POST to `/api/v1/workflow-type-submissions`, and confirm structured log lines appear with `submissionId=`, `tenantId=`, `msg=submission_created`.
- **Counter verification** (optional): hit `/actuator/prometheus` after several submission operations and confirm `submission_lifecycle_total{action="create",...}` is present.
