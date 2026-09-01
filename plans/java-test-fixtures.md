# Gradle testFixtures: share platform-config-engine doubles with platform-api

## Problem

`platform-config-engine`'s test doubles live in `src/test/java/`, which Gradle does not
expose to other modules. When `platform-api` tests need to construct a
`WorkflowTypeSubmissionService` (via `InMemorySubmissionPort`), they cannot import the real
doubles and fall back to anonymous no-op implementations:

```java
// InMemorySubmissionPort.java — current state (bad)
private final ISubmissionAuditRepository auditRepo = new ISubmissionAuditRepository() {
    @Override
    public void save(AuditEntry entry) { /* no-op — silently drops audit calls */ }
};
```

This means API-layer tests cannot assert that audit entries are produced, and the two
implementations drift independently.

## Solution

Apply the Gradle `java-test-fixtures` plugin to `platform-config-engine`. This creates a
`src/testFixtures/java/` source set that Gradle compiles into a `-test-fixtures` artifact
other modules can consume with `testImplementation(testFixtures(project("...")))`.

### Key properties of testFixtures (read before implementing)

- Source root: `src/testFixtures/java/` (alongside `src/main/` and `src/test/`)
- Automatically has the module's `main` source set on its compilation classpath
- Inherits the module's `implementation` dependencies (so `platform-domain` is available)
- Does NOT inherit `testImplementation` — if testFixtures classes need e.g. AssertJ, declare
  `testFixturesImplementation(libs.assertj.core)`. The doubles in this project use only JDK
  and main-source-set types, so no extra declarations are needed.
- Does NOT affect `src/test/` — existing test classes (`CucumberSuiteTest`, step definitions)
  stay in `src/test/` and can import from testFixtures normally.
- JaCoCo only reports on `build/classes/java/main/` by default, so testFixtures classes do
  not appear as uncovered production code.

## Scope

Move all four doubles from `platform-config-engine/src/test/java/com/platform/config/doubles/`
to `platform-config-engine/src/testFixtures/java/com/platform/config/doubles/`. Nothing else
changes in their package or class names.

Then update `InMemorySubmissionPort` in `platform-api` to use the real doubles instead of
anonymous no-ops, and clean up the now-redundant `store` field.

## Impacted files

| File | Change |
|---|---|
| `platform-config-engine/build.gradle.kts` | Apply `java-test-fixtures` plugin |
| `platform-config-engine/src/testFixtures/java/.../doubles/InMemorySubmissionAuditRepository.java` | **Moved** from `src/test/` — identical content |
| `platform-config-engine/src/testFixtures/java/.../doubles/InMemoryWorkflowTypeSubmissionRepository.java` | **Moved** from `src/test/` — identical content |
| `platform-config-engine/src/testFixtures/java/.../doubles/InMemoryConfigDocumentWriter.java` | **Moved** from `src/test/` — identical content |
| `platform-config-engine/src/testFixtures/java/.../doubles/InMemoryConfigDocumentRepository.java` | **Moved** from `src/test/` — identical content |
| `platform-api/build.gradle.kts` | Add `testImplementation(testFixtures(project(":platform-config-engine")))` |
| `platform-api/src/test/.../doubles/InMemorySubmissionPort.java` | Replace anonymous impls with real doubles; remove `store` field |

The step definitions and `CucumberSuiteTest` in `platform-config-engine` stay in
`src/test/` — no changes needed there because `test` can always see `testFixtures`.

## Step-by-step execution

### Step 1 — Apply the plugin

**File**: `platform-config-engine/build.gradle.kts`

Current:
```kotlin
dependencies {
    implementation(project(":platform-domain"))
    ...
}
```

New (add `plugins` block before `dependencies`):
```kotlin
plugins {
    `java-test-fixtures`
}

dependencies {
    implementation(project(":platform-domain"))
    ...
}
```

No version needed — `java-test-fixtures` is a core Gradle plugin.

### Step 2 — Create the testFixtures directory

```
platform-config-engine/src/testFixtures/java/com/platform/config/doubles/
```

Create this directory tree. The four `.java` files move here in Step 3.

### Step 3 — Move the four doubles

Move (do not copy) each file from:
```
platform-config-engine/src/test/java/com/platform/config/doubles/<Class>.java
```
to:
```
platform-config-engine/src/testFixtures/java/com/platform/config/doubles/<Class>.java
```

Files to move (package declarations and class bodies are **unchanged**):
- `InMemorySubmissionAuditRepository.java`
- `InMemoryWorkflowTypeSubmissionRepository.java`
- `InMemoryConfigDocumentWriter.java`
- `InMemoryConfigDocumentRepository.java`

After the move, `platform-config-engine/src/test/java/com/platform/config/doubles/` should
be **empty** (delete the now-empty directory).

### Step 4 — Compile check: platform-config-engine

```bash
./gradlew :platform-config-engine:compileTestFixturesJava :platform-config-engine:compileTestJava
```

Both tasks must succeed. The step definitions import from `com.platform.config.doubles` —
Gradle makes testFixtures visible to the test source set automatically, so no import changes
are needed in the step definitions.

### Step 5 — Add testFixtures dependency in platform-api

**File**: `platform-api/build.gradle.kts`

Add one line to the `dependencies` block, alongside the other `testImplementation` lines:

```kotlin
testImplementation(testFixtures(project(":platform-config-engine")))
```

### Step 6 — Rewrite InMemorySubmissionPort

**File**: `platform-api/src/test/java/com/platform/api/doubles/InMemorySubmissionPort.java`

Replace the entire file. The key changes vs. the current state:
- Remove `store` (private `ArrayList` field) — the repo double owns its own store
- Replace the anonymous `IWorkflowTypeSubmissionRepository` with `InMemoryWorkflowTypeSubmissionRepository`
- Replace the anonymous no-op `ISubmissionAuditRepository` with `InMemorySubmissionAuditRepository`
- Replace the anonymous no-op `IConfigDocumentWriter` with `InMemoryConfigDocumentWriter`
- `seed()` delegates to `repo.save()` instead of manipulating `store` directly
- `reset()` calls `repo.reset()` and `auditRepo.reset()`
- Remove the now-unused imports (`AuditEntry`, `ISubmissionAuditRepository`, `IConfigDocumentWriter`, `ArrayList`)

Full replacement:

```java
package com.platform.api.doubles;

import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.CreateSubmissionCommand;
import com.platform.config.domain.ports.in.ICreateWorkflowTypeSubmissionUseCase;
import com.platform.config.domain.ports.in.IGetSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviewSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviseSubmissionUseCase;
import com.platform.config.domain.ports.in.ISaveDraftUseCase;
import com.platform.config.domain.ports.in.ISubmitForApprovalUseCase;
import com.platform.config.domain.service.WorkflowTypeSubmissionService;
import com.platform.config.doubles.InMemoryConfigDocumentWriter;
import com.platform.config.doubles.InMemorySubmissionAuditRepository;
import com.platform.config.doubles.InMemoryWorkflowTypeSubmissionRepository;

import java.util.List;

public class InMemorySubmissionPort
        implements ICreateWorkflowTypeSubmissionUseCase,
                   ISaveDraftUseCase,
                   ISubmitForApprovalUseCase,
                   IReviewSubmissionUseCase,
                   IReviseSubmissionUseCase,
                   IGetSubmissionUseCase {

    private final InMemoryWorkflowTypeSubmissionRepository repo =
            new InMemoryWorkflowTypeSubmissionRepository();
    private final InMemorySubmissionAuditRepository auditRepo =
            new InMemorySubmissionAuditRepository();
    private final InMemoryConfigDocumentWriter writer =
            new InMemoryConfigDocumentWriter();

    private WorkflowTypeSubmissionService service() {
        return new WorkflowTypeSubmissionService(repo, writer, auditRepo, true);
    }

    public void seed(WorkflowTypeSubmission submission) {
        repo.save(submission);
    }

    public void reset() {
        repo.reset();
        auditRepo.reset();
        writer.reset();
    }

    @Override
    public WorkflowTypeSubmission create(CreateSubmissionCommand command) {
        return service().create(command);
    }

    @Override
    public WorkflowTypeSubmission saveDraft(String tenantId, String submissionId,
                                             String actorUserId, DraftConfigs partialDraftConfigs, int currentStep) {
        return service().saveDraft(tenantId, submissionId, actorUserId, partialDraftConfigs, currentStep);
    }

    @Override
    public WorkflowTypeSubmission submit(String tenantId, String submissionId, String actorUserId) {
        return service().submit(tenantId, submissionId, actorUserId);
    }

    @Override
    public WorkflowTypeSubmission approve(String tenantId, String submissionId, String reviewerUserId) {
        return service().approve(tenantId, submissionId, reviewerUserId);
    }

    @Override
    public WorkflowTypeSubmission reject(String tenantId, String submissionId,
                                          String reviewerUserId, String reason) {
        return service().reject(tenantId, submissionId, reviewerUserId, reason);
    }

    @Override
    public WorkflowTypeSubmission revise(String tenantId, String submissionId,
                                          String actorUserId, DraftConfigs updatedDraftConfigs) {
        return service().revise(tenantId, submissionId, actorUserId, updatedDraftConfigs);
    }

    @Override
    public WorkflowTypeSubmission getById(String tenantId, String submissionId) {
        return service().getById(tenantId, submissionId);
    }

    @Override
    public List<WorkflowTypeSubmission> getPendingForTenant(String tenantId) {
        return service().getPendingForTenant(tenantId);
    }

    @Override
    public List<WorkflowTypeSubmission> getDraftsForUser(String tenantId, String actorUserId) {
        return service().getDraftsForUser(tenantId, actorUserId);
    }

    @Override
    public List<WorkflowTypeSubmission> getRejectedForUser(String tenantId, String actorUserId) {
        return service().getRejectedForUser(tenantId, actorUserId);
    }

    @Override
    public List<WorkflowTypeSubmission> getAllDraftsForTenant(String tenantId) {
        return service().getAllDraftsForTenant(tenantId);
    }
}
```

### Step 7 — Full build verification

```bash
./gradlew build cucumber
```

All tasks must pass with no failures. Expected green:
- `:platform-config-engine:compileTestFixturesJava`
- `:platform-config-engine:compileTestJava`
- `:platform-config-engine:cucumber` (26 scenarios)
- `:platform-api:compileTestJava`
- `:platform-api:cucumber`
- All other module cucumbers

## Verification checklist

- [ ] `platform-config-engine/src/test/java/com/platform/config/doubles/` is empty and removed
- [ ] `platform-config-engine/src/testFixtures/java/com/platform/config/doubles/` contains all 4 doubles
- [ ] `InMemorySubmissionPort` imports from `com.platform.config.doubles` (not anonymous impls)
- [ ] `InMemorySubmissionPort.reset()` calls `repo.reset()` and `auditRepo.reset()`
- [ ] `InMemorySubmissionPort` has no `store` field or anonymous inner classes
- [ ] `./gradlew build cucumber` passes with no failures

## What this enables going forward

If API-layer Cucumber scenarios ever need to assert on audit entries (e.g. "submitting via
the REST endpoint produces a SUBMISSION_SUBMITTED_FOR_REVIEW audit entry"), the
`InMemorySubmissionAuditRepository` is already wired into `InMemorySubmissionPort`. Expose it
via a getter or as a Spring bean in `TestApiConfig` at that point — no structural change
needed.

Any other future module that depends on `platform-config-engine` and needs test doubles gets
them for free with `testImplementation(testFixtures(project(":platform-config-engine")))`.
