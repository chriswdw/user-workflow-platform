# Prompt 02 — platform-domain

## Goal
Implement the `platform-domain` module: all shared domain models, enums, sealed interfaces, output port interfaces, and the shared `FieldPathResolver` utility. This module has **zero framework dependencies** — no Spring, no Jackson, no database imports anywhere in `src/main/java`.

## Package root
`com.platform.domain`

## Files to create

### `domain/model/WorkItem.java`
Java record — the central domain entity:
```java
public record WorkItem(
    String id, String tenantId, String workflowType, String correlationId,
    String configVersionId, SourceType source, String sourceRef,
    String idempotencyKey, String status, String assignedGroup,
    boolean routedByDefault, Map<String, Object> fields,
    Integer priorityScore, String priorityLevel, Instant priorityLastCalculatedAt,
    String pendingCheckerId, String pendingCheckerTransition,
    int version, String makerUserId, Instant createdAt, Instant updatedAt
)
```
Include "with" methods returning new instances (all use `Instant.now()` for `updatedAt`):
- `withStatus(String newStatus)`
- `withAssignedGroup(String newGroup)`
- `withMakerUserId(String userId)`
- `withFields(Map<String, Object> newFields)`
- `withPendingMakerChecker(String checkerId, String transitionName)`
- `clearPendingMakerChecker()` — sets pendingCheckerId and pendingCheckerTransition to null

### `domain/model/AuditEntry.java`
```java
public record AuditEntry(
    String id, String tenantId, String workItemId, String correlationId,
    AuditEventType eventType, String previousState, String newState,
    String transitionName, List<ChangedField> changedFields,
    String actorUserId, String actorRole, Instant timestamp, String idempotencyKey
) {
    public record ChangedField(String fieldPath, Object previousValue, Object newValue) {}
}
```

### `domain/model/AuditEventType.java`
Enum with exactly these 20 values (in order):
`INGESTION`, `STATE_TRANSITION`, `FIELD_UPDATE`, `ASSIGNMENT`, `ROUTING_FALLBACK`,
`MAKER_CHECKER_APPROVAL`, `GROUP_REASSIGNMENT`, `CHILD_WORKFLOW_CREATED`,
`ATTACHED_TO_EXISTING_CHILD`, `DEPENDENCY_RESOLVED`, `SLA_WARNING`, `SLA_BREACH`,
`DUPLICATE_INGESTION_DISCARDED`, `TRANSITION_FAILED`,
`SUBMISSION_CREATED`, `SUBMISSION_SUBMITTED_FOR_REVIEW`,
`SUBMISSION_APPROVED`, `SUBMISSION_REJECTED`, `SUBMISSION_REVISED`, `SUBMISSION_DISCARDED`

### `domain/model/SourceType.java`
Enum: `KAFKA`, `DB_POLL`, `FILE_UPLOAD`, `FILE_SHARE`

### `domain/model/ConnectionType.java`
Enum: `KAFKA`, `DB_POLL`, `FILE_SHARE`

### `domain/model/ConnectionConfig.java`
Sealed interface with three permitted record implementations:
```java
public sealed interface ConnectionConfig
        permits ConnectionConfig.KafkaConfig,
                ConnectionConfig.DbPollConfig,
                ConnectionConfig.FileShareConfig {

    record KafkaConfig(String bootstrapServers, String topicName) implements ConnectionConfig {
        // compact constructor: validate bootstrapServers and topicName not blank
    }
    record DbPollConfig(String jdbcUrl, String query, int pollIntervalSeconds) implements ConnectionConfig {
        // compact constructor: validate jdbcUrl not blank; check jdbcUrl against
        // Pattern.compile("://[^@]*:[^@]+@|[?&](password|user)=", CASE_INSENSITIVE) — throw if credentials found;
        // validate query not blank; validate pollIntervalSeconds > 0
    }
    record FileShareConfig(String path, String filePattern) implements ConnectionConfig {
        // compact constructor: validate path and filePattern not blank
    }
}
```

### `domain/model/SourceConnection.java`
```java
public record SourceConnection(
    String id, String name, String displayName, ConnectionType connectionType,
    ConnectionConfig config, String credentialsRef, String createdBy,
    OffsetDateTime createdAt, OffsetDateTime updatedAt
)
```
Compact constructor validates that `config` type matches `connectionType` using a switch:
- `KAFKA` → config must be `KafkaConfig`
- `DB_POLL` → config must be `DbPollConfig`
- `FILE_SHARE` → config must be `FileShareConfig`

Throw `IllegalArgumentException` if mismatch.

### `domain/model/SourceConnectionAccess.java`
```java
public record SourceConnectionAccess(
    String id, String sourceConnectionId, String tenantId,
    String grantedBy, OffsetDateTime grantedAt
) {}
```

### `domain/model/DomainEvent.java`
```java
public record DomainEvent(
    String eventId, String tenantId, String workItemId, String correlationId,
    String eventType, Instant timestamp, Map<String, Object> payload
) {}
```

### `domain/ports/out/IAuditRepository.java`
```java
public interface IAuditRepository {
    void save(AuditEntry entry);
}
```

### `domain/ports/out/IDomainEventPublisher.java`
```java
public interface IDomainEventPublisher {
    void publish(DomainEvent event);
}
```

### `domain/shared/FieldPathResolver.java`
Final utility class with private constructor. Single public static method:
```java
public static Optional<Object> resolve(Map<String, Object> fields, String dotPath)
```
Split `dotPath` on first `.` (limit 2). Look up `parts[0]` in fields. If no more parts, return `Optional.of(value)`. If value is a `Map`, recurse with the remainder. Otherwise return `Optional.empty()`.

## Constraints
- All records are immutable — no setters
- `OffsetDateTime` used in `SourceConnection` and `SourceConnectionAccess` (JDBC driver compatibility)
- `Instant` used in `WorkItem`, `AuditEntry`, `DomainEvent` (pure domain, no JDBC concern)
- No `@SuppressWarnings` except on the `@SuppressWarnings("unchecked")` cast in `FieldPathResolver.resolve`
- No framework imports (`jakarta.*`, `org.springframework.*`, etc.) in any of these files

## Verification
```bash
./gradlew :platform-domain:build   # Must compile with no errors
```
