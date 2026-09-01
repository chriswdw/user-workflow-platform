# Prompt 07 — platform-config-engine Domain Layer

## Goal
Implement the `platform-config-engine` domain layer: all models, enums, exceptions, input ports, output ports, and all eight domain services. This module has **no Spring dependencies** in `src/main/java`. All services are plain Java classes, constructor-injected.

## Package root
`com.platform.config`

## Models

### `domain/model/SubmissionStatus.java`
Enum: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`

### `domain/model/ConfigType.java`
Enum (9 values): `WORKFLOW_TYPE_DEFINITION`, `ROUTING_CONFIG`, `WORKFLOW_CONFIG`, `FIELD_TYPE_REGISTRY`, `RESOLUTION_GROUP`, `INGESTION_SOURCE_CONFIG`, `BLOTTER_CONFIG`, `DETAIL_VIEW_CONFIG`, `USER_ROLE_CONFIG`

### `domain/model/ConfigDocument.java`
```java
public record ConfigDocument(
    String id, String tenantId, String workflowType,
    ConfigType configType, Map<String, Object> content,
    String version, boolean active
) {}
```

### `domain/model/ConfigValidationViolation.java`
```java
public record ConfigValidationViolation(String path, String message) {}
```

### `domain/model/ConfigValidationResult.java`
```java
public record ConfigValidationResult(boolean valid, List<ConfigValidationViolation> violations) {}
```

### `domain/model/DraftConfigs.java`
```java
public record DraftConfigs(
    Map<String, Object> workflowTypeDefinition,
    Map<String, Object> fieldTypeRegistry,
    Map<String, Object> ingestionSourceConfig,
    Map<String, Object> workflowConfig,
    Map<String, Object> blotterConfig,
    Map<String, Object> detailViewConfig
) {
    public boolean isComplete() {
        return blotterConfig != null && !blotterConfig.isEmpty()
            && detailViewConfig != null && !detailViewConfig.isEmpty();
    }
}
```

### `domain/model/WorkflowTypeSubmission.java`
```java
public record WorkflowTypeSubmission(
    String id, String tenantId, String workflowType,
    String displayName, String description,
    SubmissionStatus status, String statusDisplayName,
    DraftConfigs draftConfigs,
    String submittedBy, OffsetDateTime submittedAt,
    String reviewedBy, OffsetDateTime reviewedAt,
    String rejectionReason, int currentStep,
    int version, OffsetDateTime createdAt, OffsetDateTime updatedAt
) {}
```

## Exceptions

### `domain/exception/SubmissionNotFoundException.java`
RuntimeException: `"Submission not found: " + id`

### `domain/exception/SubmissionAlreadyExistsException.java`
RuntimeException: `"A non-rejected submission already exists for workflowType: " + workflowType`

### `domain/exception/SelfApprovalException.java`
RuntimeException: `"User " + userId + " cannot approve their own submission"`

### `domain/exception/IncompleteSubmissionException.java`
RuntimeException with provided message.

### `domain/exception/ConfigNotFoundException.java`
RuntimeException: `"Config not found for workflowType=" + workflowType + " configType=" + configType + " tenantId=" + tenantId`

### `domain/exception/ConfigIntegrityException.java`
RuntimeException with provided message.

### `domain/exception/SourceConnectionNotFoundException.java`
RuntimeException: `"Source connection not found: " + id`

## Input Ports (`domain/ports/in/`)

### `CreateSubmissionCommand.java`
```java
public record CreateSubmissionCommand(
    String tenantId, String actorUserId, String workflowType,
    String displayName, String description, DraftConfigs draftConfigs
) {}
```

### `ICreateWorkflowTypeSubmissionUseCase.java`
```java
public interface ICreateWorkflowTypeSubmissionUseCase {
    WorkflowTypeSubmission create(CreateSubmissionCommand command);
}
```

### `ISaveDraftUseCase.java`
```java
public interface ISaveDraftUseCase {
    WorkflowTypeSubmission saveDraft(String tenantId, String submissionId,
        String actorUserId, DraftConfigs draftConfigs, int currentStep);
}
```

### `ISubmitForApprovalUseCase.java`
```java
public interface ISubmitForApprovalUseCase {
    WorkflowTypeSubmission submit(String tenantId, String submissionId, String actorUserId);
}
```

### `IReviewSubmissionUseCase.java`
```java
public interface IReviewSubmissionUseCase {
    WorkflowTypeSubmission approve(String tenantId, String submissionId, String reviewerUserId);
    WorkflowTypeSubmission reject(String tenantId, String submissionId, String reviewerUserId, String reason);
}
```

### `IReviseSubmissionUseCase.java`
```java
public interface IReviseSubmissionUseCase {
    WorkflowTypeSubmission revise(String tenantId, String submissionId,
        String actorUserId, DraftConfigs updatedDraftConfigs);
}
```

### `IDiscardSubmissionUseCase.java`
```java
public interface IDiscardSubmissionUseCase {
    void discard(String tenantId, String submissionId, String actorUserId, boolean isAdmin);
}
```

### `IGetSubmissionUseCase.java`
```java
public interface IGetSubmissionUseCase {
    WorkflowTypeSubmission getById(String tenantId, String submissionId);
    List<WorkflowTypeSubmission> getPendingForTenant(String tenantId);
    List<WorkflowTypeSubmission> getDraftsForUser(String tenantId, String userId);
    List<WorkflowTypeSubmission> getRejectedForUser(String tenantId, String userId);
    List<WorkflowTypeSubmission> getAllDraftsForTenant(String tenantId);
}
```

### `ILoadConfigUseCase.java`
```java
public interface ILoadConfigUseCase {
    ConfigDocument load(String tenantId, String workflowType, ConfigType configType);
    List<ConfigDocument> loadAll(String tenantId, String workflowType);
}
```

### `IValidateConfigsUseCase.java`
```java
public interface IValidateConfigsUseCase {
    ConfigValidationResult validate(String tenantId, String workflowType);
}
```

### `IManageSourceConnectionsUseCase.java`
```java
public interface IManageSourceConnectionsUseCase {
    SourceConnection create(SourceConnection connection);
    SourceConnection update(SourceConnection connection);
    void grantAccess(String connectionId, String tenantId, String grantedBy);
    void revokeAccess(String connectionId, String tenantId);
}
```

### `IListSourceConnectionsUseCase.java`
```java
public interface IListSourceConnectionsUseCase {
    List<SourceConnection> listForTenant(String tenantId);
    SourceConnection getById(String id);
}
```

## Output Ports (`domain/ports/out/`)

### `IWorkflowTypeSubmissionRepository.java`
```java
public interface IWorkflowTypeSubmissionRepository {
    WorkflowTypeSubmission save(WorkflowTypeSubmission submission);
    Optional<WorkflowTypeSubmission> findById(String tenantId, String id);
    boolean existsByTenantAndWorkflowType(String tenantId, String workflowType);
    List<WorkflowTypeSubmission> findPendingByTenant(String tenantId);
    List<WorkflowTypeSubmission> findDraftsByUser(String tenantId, String userId);
    List<WorkflowTypeSubmission> findRejectedByUser(String tenantId, String userId);
    List<WorkflowTypeSubmission> findAllDraftsByTenant(String tenantId);
}
```

### `IConfigDocumentRepository.java`
```java
public interface IConfigDocumentRepository {
    Optional<ConfigDocument> findActive(String tenantId, String workflowType, ConfigType configType);
    List<ConfigDocument> findAllActive(String tenantId, String workflowType);
}
```

### `IConfigDocumentWriter.java`
```java
public interface IConfigDocumentWriter {
    void saveAll(List<ConfigDocument> documents);
}
```

### `ISourceConnectionRepository.java`
```java
public interface ISourceConnectionRepository {
    SourceConnection save(SourceConnection connection);
    Optional<SourceConnection> findById(String id);
    List<SourceConnection> findByTenantId(String tenantId);
    void grantAccess(String connectionId, String tenantId, String grantedBy);
    void revokeAccess(String connectionId, String tenantId);
}
```

## Domain Services

### `domain/service/SubmissionGuards.java`
Final class, private constructor. Package-private static helpers:
- `assertStatus(submission, expected, op)` — throw `IllegalStateException` if `submission.status() != expected`
- `assertOwner(submission, actorUserId, op)` — throw `IllegalStateException` if submittedBy ≠ actorUserId
- `assertNotSelfApproval(submission, reviewerUserId)` — throw `SelfApprovalException` if equal
- `publishConfigDocuments(submission, writer)` — call `writer.saveAll(...)` with 6 `ConfigDocument` records (one per DraftConfigs field) using `submission.version() + 1` as version string; use `UUID.randomUUID()` for each document id
- `submissionAuditEntry(submission, eventType, previousState, newState, actorUserId)` — create `AuditEntry` with `workItemId = submission.id()`, `correlationId = null`, idempotencyKey = `submissionId + ":" + eventType.name() + ":" + version`

### `domain/service/SubmissionCreationService.java`
Implements `ICreateWorkflowTypeSubmissionUseCase`. Constructor:
```java
public SubmissionCreationService(
    IWorkflowTypeSubmissionRepository repo,
    IConfigDocumentWriter configDocumentWriter,
    IAuditRepository auditRepo,
    boolean makerCheckerEnabled
)
```
`create(command)`:
1. Validate `workflowType` matches `^[A-Z][A-Z0-9_]*$` (throw `IllegalArgumentException`)
2. Check `repo.existsByTenantAndWorkflowType(...)` — throw `SubmissionAlreadyExistsException`
3. Build submission with `status=DRAFT`, `statusDisplayName="Draft"`, `currentStep=1`, `version=1`
4. Save, write `SUBMISSION_CREATED` audit entry
5. If `!makerCheckerEnabled`: auto-approve (publish config docs, transition to APPROVED with `version+1`, write `SUBMISSION_APPROVED` audit entry)
6. Return saved submission

### `domain/service/SubmissionDraftService.java`
Implements `ISaveDraftUseCase`. Constructor: `(IWorkflowTypeSubmissionRepository repo, IAuditRepository auditRepo)`.
`saveDraft(...)`: load submission, `assertStatus(DRAFT)`, `assertOwner`, update `draftConfigs` and `currentStep`, increment version, save. No audit entry for draft saves.

### `domain/service/SubmissionLifecycleService.java`
Implements `ISubmitForApprovalUseCase` and `IReviseSubmissionUseCase`. Constructor: `(IWorkflowTypeSubmissionRepository repo, IAuditRepository auditRepo)`.
- `submit(...)`: `assertStatus(DRAFT)`, `assertOwner`, `draftConfigs.isComplete()` check (throw `IncompleteSubmissionException`), transition to `PENDING_APPROVAL` with `submittedAt=now`, write `SUBMISSION_SUBMITTED_FOR_REVIEW` audit entry
- `revise(...)`: `assertStatus(REJECTED)`, `assertOwner`, replace draftConfigs, reset to `DRAFT`, `currentStep=1`, write `SUBMISSION_REVISED` audit entry

### `domain/service/SubmissionReviewService.java`
Implements `IReviewSubmissionUseCase`. Constructor: `(IWorkflowTypeSubmissionRepository repo, IConfigDocumentWriter configDocumentWriter, IAuditRepository auditRepo)`.
- `approve(...)`: `assertStatus(PENDING_APPROVAL)`, `assertNotSelfApproval`, publish config docs, transition to `APPROVED`, write `SUBMISSION_APPROVED` audit entry
- `reject(...)`: `assertStatus(PENDING_APPROVAL)`, `assertNotSelfApproval`, transition to `REJECTED` with `rejectionReason`, write `SUBMISSION_REJECTED` audit entry

### `domain/service/SubmissionQueryService.java`
Implements `IGetSubmissionUseCase`. Constructor: `(IWorkflowTypeSubmissionRepository repo)`.
Delegates each method to the repo. `getById` wraps in `orElseThrow(SubmissionNotFoundException)`.

### `domain/service/DiscardSubmissionService.java`
Implements `IDiscardSubmissionUseCase`. Constructor: `(IWorkflowTypeSubmissionRepository repo, IAuditRepository auditRepo)`.
`discard(tenantId, submissionId, actorUserId, isAdmin)`:
- Load submission (throw `SubmissionNotFoundException`)
- If not admin: `assertOwner`, and status must be `DRAFT` or `REJECTED` (throw `IllegalStateException` otherwise)
- If admin: allow discarding any status
- Delete from repo (`repo.delete(tenantId, submissionId)` — add this method to `IWorkflowTypeSubmissionRepository`)
- Write `SUBMISSION_DISCARDED` audit entry

**Add to `IWorkflowTypeSubmissionRepository`:**
```java
void delete(String tenantId, String submissionId);
```

### `domain/service/ConfigService.java`
Implements `ILoadConfigUseCase` (and optionally `IValidateConfigsUseCase`). Constructor: `(IConfigDocumentRepository repo, MeterRegistry meterRegistry)`.
- `load(tenantId, workflowType, configType)`: `repo.findActive(...)` or throw `ConfigNotFoundException`; record counter `config.load` with tags `workflowType`, `configType`
- `loadAll(tenantId, workflowType)`: `repo.findAllActive(...)`

### `domain/service/SourceConnectionService.java`
Implements `IManageSourceConnectionsUseCase` and `IListSourceConnectionsUseCase`. Constructor: `(ISourceConnectionRepository repo)`.
- `create(connection)`: `repo.save(connection)`
- `update(connection)`: verify exists via `findById`, then `repo.save(connection)`
- `grantAccess(connectionId, tenantId, grantedBy)`: verify connection exists, then `repo.grantAccess(...)`
- `revokeAccess(connectionId, tenantId)`: `repo.revokeAccess(...)`
- `listForTenant(tenantId)`: `repo.findByTenantId(tenantId)`
- `getById(id)`: `repo.findById(id).orElseThrow(SourceConnectionNotFoundException)`

## Constraints
- All `OffsetDateTime` values use `OffsetDateTime.now(ZoneOffset.UTC)`
- `SubmissionGuards` is package-private (`final class`, no `public` modifier)
- Services depend only on port interfaces — never on concrete classes
- No `@Autowired`, no Spring annotations anywhere in `src/main/java`

## Verification
```bash
./gradlew :platform-config-engine:build
```
(Tests will be added in Prompt 08)
