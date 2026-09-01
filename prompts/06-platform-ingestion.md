# Prompt 06 — platform-ingestion

## Goal
Implement the `platform-ingestion` module: raw record normalisation, idempotency key strategies (EXPLICIT_FIELD and COMPOSITE_HASH with SHA-256), `IngestionService`, in-memory test doubles, and Cucumber BDD tests. Uses Micrometer for ingestion counters.

## Package root
`com.platform.ingestion`

## Production files

### `domain/model/IdempotencyKeyStrategy.java`
Enum: `EXPLICIT_FIELD`, `COMPOSITE_HASH`

### `domain/model/UnknownColumnPolicy.java`
Enum: `IGNORE`, `REJECT`

### `domain/model/FieldMapping.java`
```java
public record FieldMapping(
    String sourceField,   // column name in raw record
    String targetPath,    // dot-notation path in WorkItem.fields
    String fieldType      // e.g. "STRING", "MONETARY", "DATE"
) {}
```

### `domain/model/IngestionConfig.java`
```java
public record IngestionConfig(
    String id,
    String tenantId,
    String workflowType,
    String sourceConnectionId,
    List<FieldMapping> fieldMappings,
    IdempotencyKeyStrategy idempotencyKeyStrategy,
    String explicitIdempotencyField,       // used when strategy=EXPLICIT_FIELD
    List<String> compositeHashFields,      // used when strategy=COMPOSITE_HASH
    UnknownColumnPolicy unknownColumnPolicy,
    String initialStatus,
    String initialGroup
)
```
Compact constructor validates:
- `tenantId`, `workflowType`, `initialStatus`, `initialGroup` non-null
- If `EXPLICIT_FIELD`: `explicitIdempotencyField` must not be blank
- If `COMPOSITE_HASH`: `compositeHashFields` must not be empty

### `domain/model/RawInboundRecord.java`
```java
public record RawInboundRecord(
    String tenantId,
    String workflowType,
    String correlationId,
    String sourceRef,
    SourceType source,
    Map<String, String> rawFields
) {}
```

### `domain/model/IngestionResult.java`
Sealed interface:
```java
public sealed interface IngestionResult
        permits IngestionResult.Created,
                IngestionResult.Duplicate,
                IngestionResult.Rejected {

    record Created(WorkItem workItem) implements IngestionResult {}
    record Duplicate(String idempotencyKey) implements IngestionResult {}
    record Rejected(String reason) implements IngestionResult {}
}
```

### `domain/exception/DuplicateIdempotencyKeyException.java`
RuntimeException: `"Duplicate idempotency key: " + key`

### `domain/exception/IngestionConfigNotFoundException.java`
RuntimeException: `"No ingestion config for workflowType=" + workflowType + " tenantId=" + tenantId`

### `domain/ports/in/IIngestRecordUseCase.java`
```java
public interface IIngestRecordUseCase {
    IngestionResult ingest(RawInboundRecord record);
}
```

### `domain/ports/out/IIngestionConfigRepository.java`
```java
public interface IIngestionConfigRepository {
    Optional<IngestionConfig> findByTenantAndWorkflowType(String tenantId, String workflowType);
}
```

### `domain/ports/out/IIdempotencyKeyRepository.java`
```java
public interface IIdempotencyKeyRepository {
    boolean exists(String tenantId, String workflowType, String idempotencyKey);
    void save(String tenantId, String workflowType, String idempotencyKey);
}
```

### `domain/ports/out/IIngestionWorkItemRepository.java`
```java
public interface IIngestionWorkItemRepository {
    WorkItem insert(WorkItem workItem);
}
```

### `domain/ports/out/IGroupAssignmentPort.java`
```java
public interface IGroupAssignmentPort {
    String assignGroup(String tenantId, String workflowType, Map<String, Object> fields);
}
```

### `domain/service/IngestionService.java`
Implements `IIngestRecordUseCase`. Constructor:
```java
public IngestionService(
    IIngestionConfigRepository configRepository,
    IIdempotencyKeyRepository idempotencyKeyRepository,
    IIngestionWorkItemRepository workItemRepository,
    IGroupAssignmentPort groupAssignmentPort,
    IAuditRepository auditRepository,
    MeterRegistry meterRegistry
)
```

`ingest(RawInboundRecord record)` algorithm:
1. Load `IngestionConfig` (throw `IngestionConfigNotFoundException` if absent)
2. Compute `idempotencyKey`:
   - `EXPLICIT_FIELD`: `record.rawFields().get(config.explicitIdempotencyField())` (null → `Rejected("missing idempotency field")`)
   - `COMPOSITE_HASH`: SHA-256 hex of concatenated values of `config.compositeHashFields()` joined by `|`. Use `MessageDigest.getInstance("SHA-256")`.
3. Check `idempotencyKeyRepository.exists(...)` — if true, return `Duplicate(idempotencyKey)`
4. Apply `fieldMappings` to build `Map<String, Object> fields` using dot-notation nesting (same `setNestedValue` pattern as WorkflowService — split on `.`, recurse into HashMap)
5. Unknown fields in `rawFields` not in any `fieldMapping.sourceField()`: if `unknownColumnPolicy=REJECT`, return `Rejected("unknown field: " + fieldName)`; if `IGNORE`, skip
6. Assign group via `groupAssignmentPort.assignGroup(...)`
7. Build `WorkItem` with `UUID.randomUUID()` id, `status=config.initialStatus()`, `assignedGroup`, `version=1`, `createdAt=Instant.now()`, `updatedAt=Instant.now()`
8. Save idempotency key, insert work item, write INGESTION audit entry
9. Record counter `ingestion.count` with tags `workflowType`, `source`
10. Return `Created(workItem)`

## Test files

### `src/test/java/com/platform/ingestion/doubles/`
- `InMemoryIngestionConfigRepository` — implements `IIngestionConfigRepository`
- `InMemoryIdempotencyKeyRepository` — implements `IIdempotencyKeyRepository`; back with `Set<String>` keyed on `tenantId:workflowType:key`
- `InMemoryIngestionWorkItemRepository` — implements `IIngestionWorkItemRepository`; just returns the work item unchanged
- `InMemoryIngestionAuditRepository` — implements `IAuditRepository`
- `StubGroupAssignmentPort` — implements `IGroupAssignmentPort`; returns a configurable group name

### `src/test/resources/features/ingestion/ingestion.feature`
Cover:
- Happy path — new record creates WorkItem
- Duplicate idempotency key returns Duplicate
- Missing explicit idempotency field returns Rejected
- COMPOSITE_HASH strategy produces deterministic key
- Unknown column with REJECT policy returns Rejected
- Unknown column with IGNORE policy creates WorkItem

### `src/test/java/com/platform/ingestion/steps/IngestionStepDefinitions.java`
Spring-free. Wire `IngestionService` with all in-memory doubles.

### `src/test/java/com/platform/ingestion/domain/service/IngestionServiceTest.java`
JUnit 5 unit tests covering the same cases plus concurrent duplicate (two threads calling `ingest` simultaneously with the same key — verify only one `Created` result).

## Constraints
- SHA-256 via `java.security.MessageDigest` — no third-party library
- `BigDecimal` not required here (field mapping is type-agnostic — values stored as `Object`)
- Group assignment is a port — `IngestionService` never calls routing directly
- Idempotency key save must happen **before** work item insert to prevent partial commits

## Verification
```bash
./gradlew :platform-ingestion:cucumber
./gradlew :platform-ingestion:test
```
