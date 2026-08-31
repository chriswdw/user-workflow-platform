# Plan: Delete Abandoned Draft Submissions

## Context

The draft submissions blotter (`AllDraftsAdminView`) and user's own submissions view (`MySubmissionsView`) have no way to remove abandoned or unwanted entries. DRAFT and REJECTED submissions pile up indefinitely, cluttering both views. This adds a discard action so:
- **PLATFORM_ADMIN** can delete any DRAFT from `AllDraftsAdminView`
- **Submission owner** can delete their own DRAFT or REJECTED from `MySubmissionsView`

Discardable statuses: DRAFT and REJECTED. PENDING_APPROVAL and APPROVED submissions cannot be discarded (invariant enforced in domain).

---

## Implementation Steps

### Step 1 — BDD scenarios first (verify RED)

**`platform-config-engine/src/test/resources/features/config/workflow-type-submission.feature`** — add:
```gherkin
Scenario: Owner can discard their own draft submission
  Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
  When user "alice" discards the submission as owner
  Then the submission is deleted
  And a SUBMISSION_DISCARDED audit entry is recorded

Scenario: Admin can discard any draft submission
  Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
  When user "bob" discards the submission as admin
  Then the submission is deleted
  And a SUBMISSION_DISCARDED audit entry is recorded

Scenario: Owner can discard their own rejected submission
  Given a REJECTED submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
  When user "alice" discards the submission as owner
  Then the submission is deleted

Scenario: Non-owner cannot discard another user's draft
  Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
  When user "bob" discards the submission as owner
  Then an IllegalStateException is thrown

Scenario: Pending approval submission cannot be discarded
  Given a PENDING_APPROVAL submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
  When user "alice" discards the submission as owner
  Then an IllegalStateException is thrown
```

Run `./gradlew :platform-config-engine:cucumber` — new scenarios must **FAIL**.

---

### Step 2 — Domain: AuditEventType

**`platform-domain/src/main/java/com/platform/domain/model/AuditEventType.java`**
- Add `SUBMISSION_DISCARDED` to the enum

---

### Step 3 — Domain: Output port

**`platform-config-engine/src/main/java/com/platform/config/domain/ports/out/IWorkflowTypeSubmissionRepository.java`**
- Add: `void deleteById(String tenantId, String submissionId)`

---

### Step 4 — Domain: Input port

New file: **`platform-config-engine/src/main/java/com/platform/config/domain/ports/in/IDiscardSubmissionUseCase.java`**
```java
public interface IDiscardSubmissionUseCase {
    void discard(String tenantId, String submissionId, String actorUserId, boolean isAdmin);
}
```

---

### Step 5 — Domain: Service implementation

**`platform-config-engine/src/main/java/com/platform/config/domain/service/WorkflowTypeSubmissionService.java`**
- Add `implements IDiscardSubmissionUseCase`
- Logic:
  1. `load(tenantId, submissionId)` — throws `SubmissionNotFoundException` if absent
  2. Assert status is DRAFT or REJECTED — throw `IllegalStateException` for PENDING_APPROVAL / APPROVED
  3. If `!isAdmin`: call existing `assertOwner(submission, actorUserId, "discard")`
  4. `repo.deleteById(tenantId, submissionId)`
  5. `auditRepo.save(...)` with `SUBMISSION_DISCARDED`, previousState = `submission.status().name()`, newState = `"DISCARDED"`, actor = `actorUserId`

---

### Step 6 — Test doubles

**`platform-config-engine/src/testFixtures/java/com/platform/config/doubles/InMemoryWorkflowTypeSubmissionRepository.java`**
- Add: `void deleteById(String tenantId, String submissionId)` — `store.removeIf(s -> s.tenantId().equals(tenantId) && s.id().equals(submissionId))`

**`platform-api/src/test/java/com/platform/api/doubles/InMemorySubmissionPort.java`**
- Add `deleteById` implementation (same pattern)

Run `./gradlew :platform-config-engine:cucumber` — new scenarios must now **PASS**.

---

### Step 7 — JDBC adapter

**`platform-api/src/main/java/com/platform/api/adapter/out/postgres/WorkflowTypeSubmissionJdbcRepository.java`**
- Add `deleteById`:
  ```java
  jdbc.update("DELETE FROM workflow_type_submissions WHERE tenant_id = :tenantId AND id = :id",
      Map.of("tenantId", tenantId, "id", submissionId));
  ```
  No Liquibase changeset needed — no schema change.

---

### Step 8 — REST controller

**`platform-api/src/main/java/com/platform/api/adapter/in/rest/WorkflowTypeSubmissionController.java`**
- Inject `IDiscardSubmissionUseCase discardUseCase`
- Add endpoint:
  ```java
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> discard(@PathVariable String id,
                                       @AuthenticationPrincipal ApiAuthentication auth) {
      try {
          discardUseCase.discard(auth.tenantId(), id, auth.userId(), auth.isAdmin());
          return ResponseEntity.noContent().build();
      } catch (SubmissionNotFoundException e) {
          return ResponseEntity.notFound().build();
      } catch (IllegalStateException e) {
          return ResponseEntity.unprocessableEntity().build();
      }
  }
  ```
  Note: verify the method name on `ApiAuthentication` that exposes admin/role status (likely `isAdmin()` or `hasRole("PLATFORM_ADMIN")`).

---

### Step 9 — API Cucumber scenarios

**`platform-api/src/test/resources/features/api/workflow-type-submissions.feature`** — add:
```gherkin
Scenario: Admin deletes a draft submission via API
  Given a draft submission exists
  When a PLATFORM_ADMIN sends DELETE /workflow-type-submissions/{id}
  Then the response status is 204
  And the submission no longer exists

Scenario: Owner deletes their own draft submission via API
  Given a draft submission exists owned by the calling user
  When the owner sends DELETE /workflow-type-submissions/{id}
  Then the response status is 204

Scenario: Non-owner non-admin cannot delete a draft
  Given a draft submission exists owned by another user
  When a non-admin non-owner user sends DELETE /workflow-type-submissions/{id}
  Then the response status is 422
```

---

### Step 10 — Frontend: mutation hook

**`platform-frontend/src/api/useSubmissionActions.ts`**
```typescript
export function useDiscardSubmission(submissionId: string) {
  const queryClient = useQueryClient();
  return useMutation<void, Error, void>({
    mutationFn: async () => {
      await client.delete(`/workflow-type-submissions/${submissionId}`);
    },
    onSuccess: () => invalidateSubmission(queryClient, submissionId),
  });
}
```

---

### Step 11 — Frontend: AllDraftsAdminView

**`platform-frontend/src/components/admin/AllDraftsAdminView.tsx`**

In `SubmissionDetail`, extend `SubmissionDetailProps` to include `readonly onDiscard: () => void` (or handle inline). Add a Discard button:
- Use `useDiscardSubmission(sub.id)`
- Show only when `sub.statusCode === 'DRAFT'`
- On click: `window.confirm('Discard this draft? This cannot be undone.')` — proceed only if confirmed
- On success: call `onClose()` — query invalidation removes the row automatically
- Style: destructive button class (check existing styles for `btn-danger` or equivalent)

---

### Step 12 — Frontend: MySubmissionsView

**`platform-frontend/src/components/wizard/MySubmissionsView.tsx`**

For each draft/rejected submission:
- Use `useDiscardSubmission(sub.id)`
- Show Discard button for `statusCode === 'DRAFT'` and `statusCode === 'REJECTED'`
- Confirm before deleting (same `window.confirm` pattern)
- On success: query invalidation auto-refreshes the list

---

## Critical Files

| File | Change |
|---|---|
| `platform-domain/src/main/java/com/platform/domain/model/AuditEventType.java` | Add `SUBMISSION_DISCARDED` |
| `platform-config-engine/.../ports/in/IDiscardSubmissionUseCase.java` | **New** input port |
| `platform-config-engine/.../ports/out/IWorkflowTypeSubmissionRepository.java` | Add `deleteById` |
| `platform-config-engine/.../service/WorkflowTypeSubmissionService.java` | Implement discard |
| `platform-config-engine/src/testFixtures/.../InMemoryWorkflowTypeSubmissionRepository.java` | Add `deleteById` |
| `platform-config-engine/src/test/resources/features/config/workflow-type-submission.feature` | 5 new scenarios |
| `platform-api/.../adapter/out/postgres/WorkflowTypeSubmissionJdbcRepository.java` | Add SQL DELETE |
| `platform-api/.../adapter/in/rest/WorkflowTypeSubmissionController.java` | Add `DELETE /{id}` |
| `platform-api/src/test/java/.../doubles/InMemorySubmissionPort.java` | Add `deleteById` |
| `platform-api/src/test/resources/features/api/workflow-type-submissions.feature` | 3 new scenarios |
| `platform-frontend/src/api/useSubmissionActions.ts` | Add `useDiscardSubmission` |
| `platform-frontend/src/components/admin/AllDraftsAdminView.tsx` | Add Discard button (admin) |
| `platform-frontend/src/components/wizard/MySubmissionsView.tsx` | Add Discard button (owner) |

---

## Verification

1. `./gradlew :platform-config-engine:cucumber` — all scenarios green including new discard ones
2. `./gradlew :platform-api:cucumber` — API scenarios green
3. `./gradlew build` — full build passes; JaCoCo coverage reviewed for new service method
4. `./gradlew sonar` — no new HIGH/CRITICAL issues
5. Manual: open admin-drafts view → select a draft → click Discard → confirm → row disappears
6. Manual: open my-submissions view → click Discard on a draft/rejected → entry removed from list
