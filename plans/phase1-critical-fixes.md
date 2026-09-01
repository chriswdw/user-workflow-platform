# Phase 1 Implementation Plan: Critical Fixes
## Target file: plans/phase1-critical-fixes.md (write this there at session start)

> Companion to architecture-review.md. Actionable at a fresh-session level — every file path, code change, and test step is specified.

---

## Pre-flight corrections from deeper code review

After reading the actual source, two items from the initial review need corrections before proceeding:

**C1 (Optimistic lock) — NOT a bug.** The `ON CONFLICT DO UPDATE WHERE workflow_type_submissions.version = EXCLUDED.version - 1` pattern in `WorkflowTypeSubmissionJdbcRepository.java` is correct PostgreSQL. When the WHERE condition fails, PostgreSQL returns 0 rows affected and the `rows == 0` check at line 76 correctly throws `OptimisticLockingFailureException`. The existing test at line 72–83 of `WorkflowTypeSubmissionJdbcRepositoryTest.java` proves this works. No change needed.

**C2 (JWT authentication bypass) — NOT a bug.** `SecurityConfig.java` lines 28–32 configure an `authenticationEntryPoint` + `.anyRequest().authenticated()`. When `JwtAuthenticationFilter` passes through with an empty `SecurityContext`, Spring Security's authorization filter intercepts the request and calls the entrypoint (which sends 401) before it reaches any controller. Controllers will never receive `null` for `@AuthenticationPrincipal ApiAuthentication auth`. No NPE risk. The one real gap here (silent `JwtException` swallowing, no log output) is a low-priority operational fix, not a security bug.

**Genuine Phase 1 work: two items.**

---

## Fix 1 — Global Exception Handler (H1)

### Context
No `@RestControllerAdvice` exists. Every controller has per-method try/catch blocks returning bare HTTP status codes with no response body. Infrastructure exceptions (`OptimisticLockingFailureException`, `DataAccessException`) are entirely uncaught and will produce Spring's default error response with a raw stack trace in the body. The BDD Cucumber suite already asserts all the HTTP status codes, so it will validate this change end-to-end.

### Step 1 — Create `GlobalExceptionHandler.java`

**New file**: `platform-api/src/main/java/com/platform/api/adapter/in/rest/GlobalExceptionHandler.java`

```java
package com.platform.api.adapter.in.rest;

import com.platform.config.domain.exception.ConfigNotFoundException;
import com.platform.config.domain.exception.IncompleteSubmissionException;
import com.platform.config.domain.exception.SelfApprovalException;
import com.platform.config.domain.exception.SubmissionAlreadyExistsException;
import com.platform.config.domain.exception.SubmissionNotFoundException;
import com.platform.workflow.domain.exception.ForbiddenTransitionException;
import com.platform.workflow.domain.exception.InvalidTransitionException;
import com.platform.workflow.domain.exception.ValidationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({SubmissionNotFoundException.class, ConfigNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SubmissionAlreadyExistsException.class)
    public ProblemDetail handleConflict(SubmissionAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Resource was modified concurrently — please retry");
    }

    @ExceptionHandler({SelfApprovalException.class, ForbiddenTransitionException.class})
    public ProblemDetail handleForbidden(RuntimeException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({IncompleteSubmissionException.class, InvalidTransitionException.class,
                        ValidationFailedException.class, IllegalStateException.class})
    public ProblemDetail handleUnprocessable(RuntimeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        String correlationId = MDC.get("correlationId");
        log.error("correlationId={} Unhandled exception", correlationId, ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setDetail("An unexpected error occurred");
        if (correlationId != null) pd.setProperty("correlationId", correlationId);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setDetail(detail);
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) pd.setProperty("correlationId", correlationId);
        return pd;
    }
}
```

**Notes on design choices:**
- Uses Spring 6's `ProblemDetail` (RFC 7807) — already on the classpath via Spring Boot 3.x, no new dependencies.
- The `@ExceptionHandler` for `Exception.class` is the safety net — logs with correlationId from MDC, returns generic 500.
- `OptimisticLockingFailureException` maps to 409 (concurrent modification), not 500 — this is correct: it's a client-retriable condition.
- `IllegalStateException` maps to 422 — this covers the state machine violations currently caught in controllers (e.g. "cannot submit a REJECTED submission").

### Step 2 — Strip try/catch blocks from controllers

Once the handler is in place, controllers no longer need any try/catch. Remove them all. The controllers should become pure delegation to use cases.

**`WorkflowTypeSubmissionController.java`** — replace each method body:

`create` method (lines 60–76): remove the try/catch wrapper, keep only the use case call + 201 response:
```java
@PostMapping
@SuppressWarnings("unchecked")
public ResponseEntity<WorkflowTypeSubmissionResponse> create(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal ApiAuthentication auth) {
    var result = createUseCase.create(new CreateSubmissionCommand(
            auth.tenantId(), auth.userId(),
            (String) body.get("workflowType"),
            (String) body.get("displayName"),
            (String) body.get("description"),
            parseDraftConfigs(body)));
    return ResponseEntity.status(201).body(WorkflowTypeSubmissionResponse.from(result));
}
```

`saveDraft` method (lines 78–95): remove try/catch:
```java
@PatchMapping("/{id}")
@SuppressWarnings("unchecked")
public ResponseEntity<WorkflowTypeSubmissionResponse> saveDraft(
        @PathVariable String id,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal ApiAuthentication auth) {
    int step = body.containsKey("currentStep") ? (Integer) body.get("currentStep") : 1;
    var result = saveDraftUseCase.saveDraft(
            auth.tenantId(), id, auth.userId(),
            parseDraftConfigs(body), step);
    return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
}
```

`submit` method (lines 97–109): remove try/catch, keep only use case call + 200 response.

`approve` method (lines 111–125): remove try/catch.

`reject` method (lines 127–143): remove try/catch.

`revise` method (lines 145–159): remove try/catch.

`getById` method (lines 189–199): remove try/catch.

`discard` method (lines 201–213): remove try/catch.

**`WorkItemController.java`** — `triggerTransition` method (lines 55–71): remove the try/catch around `ForbiddenTransitionException`, keep only the use case call + 200 response:
```java
@PostMapping("/{id}/transitions")
@SuppressWarnings("unchecked")
public ResponseEntity<WorkItem> triggerTransition(@PathVariable String id,
                                                   @RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal ApiAuthentication auth) {
    Map<String, Object> additionalFields = body.containsKey("additionalFields")
            ? (Map<String, Object>) body.get("additionalFields")
            : Map.of();
    WorkItem updated = transitionUseCase.transition(new TransitionCommand(
            id, auth.tenantId(), (String) body.get("transition"),
            auth.userId(), auth.role(), additionalFields));
    return ResponseEntity.ok(updated);
}
```

**`ConfigController.java`** — `getDetailViewConfig` method (lines 26–36): remove try/catch:
```java
@GetMapping("/detail-view/{workflowType}")
public ResponseEntity<Map<String, Object>> getDetailViewConfig(
        @PathVariable String workflowType,
        @AuthenticationPrincipal ApiAuthentication auth) {
    var doc = loadConfig.loadActive(auth.tenantId(), workflowType, ConfigType.DETAIL_VIEW_CONFIG);
    return ResponseEntity.ok(doc.content());
}
```

**`SourceConnectionController.java`** — no exceptions are currently caught in this controller (authorization is handled inline via `isPlatformAdmin` checks, not via exceptions). No changes needed.

**`AuditController.java`** — no try/catch present. No changes needed.

### Step 3 — Remove now-unused exception imports from controllers

After removing try/catch, clean up the import statements in each controller. The imports from `com.platform.config.domain.exception.*` and `com.platform.workflow.domain.exception.*` in each controller file are no longer needed.

### Step 4 — Add a BDD scenario for the global error format

Add to `platform-api/src/test/resources/features/api/workflow-type-submissions.feature`:
```gherkin
Scenario: Concurrent modification returns 409
  Given a draft submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
  And the submission "sub-1" is modified by another session
  When I POST /api/v1/workflow-type-submissions/sub-1/submit with body {}
  Then the response status is 409
```

And add the step definition in `ApiStepDefinitions.java`:
```java
@Given("the submission {string} is modified by another session")
public void submissionModifiedByAnotherSession(String id) {
    submissionStore.forceVersionConflict(id);
}
```

This requires adding a `forceVersionConflict(String id)` helper to `InMemorySubmissionPort` that bumps the internal version so the next `save()` call throws `OptimisticLockingFailureException`.

### Verification
Run `./gradlew :platform-api:cucumber`. All existing scenarios must stay green — the status codes they assert are unchanged. The new concurrent modification scenario must also pass.

Then run `./gradlew :platform-api:build` for compile + unit test coverage.

---

## Fix 2 — Domain Model Invariant Validation (C3)

### Context
Three domain model records claim invariants in comments or docs but do not enforce them with constructor validation. A misconfigured JSON payload loaded from the database will pass the config loader without error and then throw a cryptic `NullPointerException` or `IllegalStateException` inside a domain service call at runtime. These should fail loudly at load time.

### 2a — `RoutingConfig.java`

**File**: `platform-routing/src/main/java/com/platform/routing/domain/model/RoutingConfig.java`

Current (lines 11–18):
```java
public record RoutingConfig(
        String id,
        String tenantId,
        String workflowType,
        String defaultGroupId,
        boolean alertOnDefault,
        List<RoutingRule> rules
) {}
```

Replace with:
```java
public record RoutingConfig(
        String id,
        String tenantId,
        String workflowType,
        String defaultGroupId,
        boolean alertOnDefault,
        List<RoutingRule> rules
) {
    public RoutingConfig {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workflowType, "workflowType must not be null");
        Objects.requireNonNull(defaultGroupId, "defaultGroupId must not be null — every routing config must have a fallback group");
        Objects.requireNonNull(rules, "rules must not be null");
    }
}
```

Add import: `import java.util.Objects;`

The comment block (lines 5–10) can be removed — the constructor now enforces what the comment described.

### 2b — `WorkflowConfig.java`

**File**: `platform-workflow/src/main/java/com/platform/workflow/domain/model/WorkflowConfig.java`

Add a compact constructor after the record component list (after line 13, before the existing `findTransition` method):

```java
public WorkflowConfig {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(workflowType, "workflowType must not be null");
    Objects.requireNonNull(initialState, "initialState must not be null");
    Objects.requireNonNull(states, "states must not be null");
    Objects.requireNonNull(transitions, "transitions must not be null");

    var stateNames = states.stream()
            .map(WorkflowState::name)
            .collect(java.util.stream.Collectors.toSet());

    if (!stateNames.contains(initialState)) {
        throw new IllegalArgumentException(
                "WorkflowConfig for '%s/%s': initialState '%s' is not defined in states %s"
                        .formatted(tenantId, workflowType, initialState, stateNames));
    }
    for (var t : transitions) {
        if (!stateNames.contains(t.fromState())) {
            throw new IllegalArgumentException(
                    "WorkflowConfig for '%s/%s': transition '%s' references unknown fromState '%s'"
                            .formatted(tenantId, workflowType, t.name(), t.fromState()));
        }
        if (!stateNames.contains(t.toState())) {
            throw new IllegalArgumentException(
                    "WorkflowConfig for '%s/%s': transition '%s' references unknown toState '%s'"
                            .formatted(tenantId, workflowType, t.name(), t.toState()));
        }
    }
}
```

Add imports:
```java
import java.util.Objects;
import java.util.stream.Collectors;
```

### 2c — `IngestionConfig.java`

**File**: `platform-ingestion/src/main/java/com/platform/ingestion/domain/model/IngestionConfig.java`

Add a compact constructor after the record component list (line 20, before `}`):

```java
public IngestionConfig {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(workflowType, "workflowType must not be null");
    Objects.requireNonNull(sourceType, "sourceType must not be null");
    Objects.requireNonNull(idempotencyKeyStrategy, "idempotencyKeyStrategy must not be null");
    Objects.requireNonNull(initialState, "initialState must not be null");

    if (idempotencyKeyStrategy == IdempotencyKeyStrategy.EXPLICIT_FIELD) {
        if (idempotencyExplicitField == null || idempotencyExplicitField.isBlank()) {
            throw new IllegalArgumentException(
                    "IngestionConfig for '%s/%s': idempotencyExplicitField must be set when strategy is EXPLICIT_FIELD"
                            .formatted(tenantId, workflowType));
        }
    } else if (idempotencyKeyStrategy == IdempotencyKeyStrategy.COMPOSITE_HASH) {
        if (idempotencyKeyFields == null || idempotencyKeyFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "IngestionConfig for '%s/%s': idempotencyKeyFields must be non-empty when strategy is COMPOSITE_HASH"
                            .formatted(tenantId, workflowType));
        }
    }
}
```

Add import: `import java.util.Objects;`

### Step — Write unit tests for all three constructors

**New file**: `platform-routing/src/test/java/com/platform/routing/domain/model/RoutingConfigTest.java`

```java
package com.platform.routing.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingConfigTest {

    @Test
    void constructor_nullDefaultGroupId_throws() {
        assertThatThrownBy(() -> new RoutingConfig("id", "tenant", "TYPE", null, false, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("defaultGroupId");
    }

    @Test
    void constructor_valid_succeeds() {
        new RoutingConfig("id", "tenant", "TYPE", "group-1", false, List.of());
    }
}
```

**New file**: `platform-workflow/src/test/java/com/platform/workflow/domain/model/WorkflowConfigTest.java`

(Check first if this file already exists — `WorkflowConfigTest.java` was mentioned as existing in the review. If it does, add test methods to it rather than creating a new file. If it only has `findTransition`/`findState` tests, add the following:)

```java
@Test
void constructor_initialStateNotInStates_throws() {
    var states = List.of(new WorkflowState("OPEN", false, List.of("ANALYST")));
    var transitions = List.of();
    assertThatThrownBy(() -> new WorkflowConfig("id", "t1", "TYPE", "NONEXISTENT", states, transitions, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("initialState")
            .hasMessageContaining("NONEXISTENT");
}

@Test
void constructor_transitionReferencesUnknownFromState_throws() {
    var states = List.of(new WorkflowState("OPEN", false, List.of()));
    var transitions = List.of(new WorkflowTransition(
            "close", "UNKNOWN", "OPEN", TransitionTrigger.MANUAL, null, List.of(), false, List.of(), List.of()));
    assertThatThrownBy(() -> new WorkflowConfig("id", "t1", "TYPE", "OPEN", states, transitions, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fromState")
            .hasMessageContaining("UNKNOWN");
}

@Test
void constructor_valid_succeeds() {
    var states = List.of(new WorkflowState("OPEN", false, List.of()),
                          new WorkflowState("CLOSED", true, List.of()));
    var transitions = List.of(new WorkflowTransition(
            "close", "OPEN", "CLOSED", TransitionTrigger.MANUAL, null, List.of(), false, List.of(), List.of()));
    new WorkflowConfig("id", "t1", "TYPE", "OPEN", states, transitions, true);
}
```

**New file**: `platform-ingestion/src/test/java/com/platform/ingestion/domain/model/IngestionConfigTest.java`

```java
package com.platform.ingestion.domain.model;

import com.platform.domain.model.SourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionConfigTest {

    @Test
    void constructor_explicitFieldStrategyWithNoField_throws() {
        assertThatThrownBy(() -> new IngestionConfig(
                "tenant", "TYPE", SourceType.KAFKA, List.of(),
                UnknownColumnPolicy.IGNORE, IdempotencyKeyStrategy.EXPLICIT_FIELD,
                null, null, "OPEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyExplicitField")
                .hasMessageContaining("EXPLICIT_FIELD");
    }

    @Test
    void constructor_compositeHashWithNoFields_throws() {
        assertThatThrownBy(() -> new IngestionConfig(
                "tenant", "TYPE", SourceType.KAFKA, List.of(),
                UnknownColumnPolicy.IGNORE, IdempotencyKeyStrategy.COMPOSITE_HASH,
                List.of(), null, "OPEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKeyFields")
                .hasMessageContaining("COMPOSITE_HASH");
    }

    @Test
    void constructor_explicitFieldStrategyWithField_succeeds() {
        new IngestionConfig(
                "tenant", "TYPE", SourceType.KAFKA, List.of(),
                UnknownColumnPolicy.IGNORE, IdempotencyKeyStrategy.EXPLICIT_FIELD,
                null, "trade_id", "OPEN");
    }
}
```

### Verification
```bash
./gradlew :platform-routing:test
./gradlew :platform-workflow:test
./gradlew :platform-ingestion:test
```

All three new test classes must pass. Existing tests must not be broken by the constructor changes — if any existing test in these modules constructs a `WorkflowConfig`, `RoutingConfig`, or `IngestionConfig` with null required fields, fix those tests to pass valid values.

---

## Execution order

1. Fix 2 (domain invariants) first — no dependencies, pure domain layer, no Spring context changes
2. Fix 1 (global exception handler) second — depends on knowing the full exception inventory (now confirmed)

## Full verification after both fixes

```bash
./gradlew build cucumber
```

Must produce zero failures. The BDD suite in `platform-api` exercises every HTTP status code path via MockMvc, so it validates that the exception handler correctly maps every domain exception to the expected status.
