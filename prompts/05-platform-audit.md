# Prompt 05 — platform-audit

## Goal
Implement the `platform-audit` module: the audit service, query model, output port for querying, in-memory test double, and Cucumber BDD tests. The audit log is append-only — no updates or deletes.

## Package root
`com.platform.audit`

## Production files

### `domain/model/AuditQuery.java`
```java
public record AuditQuery(
    String tenantId,
    String workItemId,
    String correlationId,
    AuditEventType eventType,   // nullable — filter by type
    Instant from,               // nullable
    Instant to,                 // nullable
    int page,
    int pageSize
) {}
```
Import `AuditEventType` and `Instant`.

### `domain/ports/in/IAppendAuditEntryUseCase.java`
```java
public interface IAppendAuditEntryUseCase {
    void append(AuditEntry entry);
}
```

### `domain/ports/in/IQueryAuditTrailUseCase.java`
```java
public interface IQueryAuditTrailUseCase {
    List<AuditEntry> query(AuditQuery query);
}
```

### `domain/ports/out/IAuditEntryRepository.java`
```java
public interface IAuditEntryRepository extends IAuditRepository {
    List<AuditEntry> findByWorkItem(String tenantId, String workItemId);
    List<AuditEntry> findByQuery(AuditQuery query);
}
```
Extends `com.platform.domain.ports.out.IAuditRepository` (which defines `void save(AuditEntry)`).

### `domain/service/AuditService.java`
Implements both `IAppendAuditEntryUseCase` and `IQueryAuditTrailUseCase`. Constructor:
```java
public AuditService(IAuditEntryRepository repository)
```
- `append(AuditEntry entry)` → `repository.save(entry)` (idempotent — duplicate idempotency keys silently ignored in the repo)
- `query(AuditQuery query)` → `repository.findByQuery(query)`

## Test files

### `src/test/java/com/platform/audit/doubles/InMemoryAuditEntryRepository.java`
Implements `IAuditEntryRepository`. Backed by `List<AuditEntry>`.
- `save(entry)`: add to list (skip if `entry.idempotencyKey()` already present)
- `findByWorkItem(tenantId, workItemId)`: filter by both
- `findByQuery(query)`: filter by `tenantId`, optionally `workItemId`, `correlationId`, `eventType`, `from`/`to`; then apply `page`/`pageSize` offset

### `src/test/java/com/platform/audit/CucumberSuiteTest.java`
```java
@Suite @IncludeEngines("cucumber")
@SelectClasspathResource("features/audit")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.platform.audit.steps")
public class CucumberSuiteTest {}
```

### `src/test/resources/features/audit/audit.feature`
```gherkin
Feature: Audit trail

  Scenario: Append and query audit entries
    Given an audit entry for work item "wi-001" with event type STATE_TRANSITION
    When the audit entry is appended
    Then querying by work item "wi-001" returns 1 entry

  Scenario: Idempotent append skips duplicate keys
    Given an audit entry for work item "wi-002" with idempotency key "key-1"
    When the same entry is appended twice
    Then querying by work item "wi-002" returns 1 entry

  Scenario: Filter by event type
    Given audit entries for work item "wi-003" with types STATE_TRANSITION and FIELD_UPDATE
    When querying with event type filter STATE_TRANSITION
    Then 1 entry is returned
```

### `src/test/java/com/platform/audit/steps/AuditStepDefinitions.java`
Spring-free. Wire `AuditService` with `InMemoryAuditEntryRepository`.

## Constraints
- `IAuditEntryRepository` extends `IAuditRepository` — this is how `platform-api` satisfies both the write-only port (used by all domain services) and the read port (used by `AuditService`)
- `AuditService` depends only on `IAuditEntryRepository` — never on `IAuditRepository` directly
- No Spring, no JUnit `@SpringBootTest` in any class

## Verification
```bash
./gradlew :platform-audit:cucumber
./gradlew :platform-audit:test
```
