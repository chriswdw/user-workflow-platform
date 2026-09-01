# Prompt 13 — platform-api Tests (BDD + Integration)

## Goal
Add the API-layer BDD tests (using Spring MockMvc + in-memory port doubles), JDBC repository integration tests (using `EmbeddedPostgres`), and a Kafka integration test. All tests must pass with `./gradlew :platform-api:test`.

## BDD tests (Cucumber + Spring MockMvc)

### `src/test/java/com/platform/api/CucumberSuiteTest.java`
```java
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/api")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.platform.api.steps")
public class CucumberSuiteTest {}
```

### `src/test/java/com/platform/api/config/TestApiConfig.java`
```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TestApiConfig {
    @TestConfiguration
    static class Doubles {
        @Bean @Primary InMemoryWorkItemQueryPort workItemQueryPort()
        @Bean @Primary InMemoryTransitionPort transitionPort(InMemoryWorkItemQueryPort queryPort)
        @Bean @Primary InMemoryAuditQueryPort auditQueryPort()
        @Bean @Primary InMemoryConfigPort configPort()
        @Bean @Primary InMemorySubmissionPort submissionPort()
        @Bean @Primary InMemorySourceConnectionPort sourceConnectionPort()
    }
}
```

### In-memory port doubles (`src/test/java/com/platform/api/doubles/`)

**`InMemoryWorkItemQueryPort.java`** — implements `IFindWorkItemPort` + `IListWorkItemsPort`. Backed by `Map<String, WorkItem>`. `put(workItem)` for test setup.

**`InMemoryTransitionPort.java`** — implements `ITransitionWorkItemUseCase`. On `transition(command)`: find work item in `InMemoryWorkItemQueryPort`, apply status change, return updated work item. For optimistic lock testing, a `forceOptimisticLockFailure(id)` method stores a flag.

**`InMemoryAuditQueryPort.java`** — implements `IQueryAuditTrailUseCase`. Backed by `List<AuditEntry>`.

**`InMemoryConfigPort.java`** — implements `ILoadConfigUseCase`. Backed by `Map` of pre-loaded config documents.

**`InMemorySubmissionPort.java`** — implements all 7 submission use case interfaces (`ICreateWorkflowTypeSubmissionUseCase`, `ISaveDraftUseCase`, `ISubmitForApprovalUseCase`, `IReviewSubmissionUseCase`, `IReviseSubmissionUseCase`, `IGetSubmissionUseCase`, `IDiscardSubmissionUseCase`). Backed by `InMemoryWorkflowTypeSubmissionRepository` from `testFixtures(project(":platform-config-engine"))`. Wires all services internally.

**`InMemorySourceConnectionPort.java`** — implements `IManageSourceConnectionsUseCase` + `IListSourceConnectionsUseCase`. Backed by in-memory map.

### Feature files (already specified — do NOT recreate)
The feature files are already defined in Prompt 11 (api.feature for work items) and shown in the conversation context (workflow-type-submissions.feature, source-connections.feature). Use those exact scenario text.

### `src/test/resources/features/api/api.feature`
Cover work-item scenarios:
- GET /work-items returns list for authenticated tenant
- GET /work-items/{id} returns 200 for existing, 404 for unknown
- POST /work-items/{id}/transitions returns 200 on success
- POST /work-items/{id}/transitions with unknown transition returns 422
- Unauthenticated access returns 401

### `src/test/java/com/platform/api/steps/ApiStepDefinitions.java`
Uses `MockMvc` (autowired), `ObjectMapper` (autowired), and shared in-memory doubles (autowired by Spring). Generates JWT tokens using `Jwts.builder()` with the test secret `dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLUhTMjU2`. Stores last `MvcResult` in thread-local field. `@Then` steps assert on `status()` and JSON response body.

---

## JDBC Integration Tests

All JDBC tests live in `src/test/java/com/platform/api/adapter/out/postgres/`.

### `EmbeddedPostgresProvider.java`
JUnit 5 `@ExtendWith` extension or base class that starts embedded PostgreSQL via `io.zonky.test:embedded-postgres`. Runs Liquibase migrations on startup. Provides `NamedParameterJdbcTemplate` and `ObjectMapper` to subclass tests.

```java
public class EmbeddedPostgresProvider {
    protected static NamedParameterJdbcTemplate jdbc;
    protected static ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeAll
    static void startPostgres() throws Exception {
        EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        // Run Liquibase
        Liquibase liquibase = new Liquibase(
                "db/changelog/db.changelog-master.yaml",
                new ClassLoaderResourceAccessor(),
                new JdbcConnection(ds.getConnection()));
        liquibase.update("");
        jdbc = new NamedParameterJdbcTemplate(ds);
    }
}
```

### Test classes (one per repository)

**`WorkItemJdbcRepositoryTest.java`** — extends `EmbeddedPostgresProvider`:
- `insertAndFindById()` — verify all fields round-trip
- `findByTenantAndWorkflowType()` — verify multi-tenant isolation
- `save_appliesOptimisticLocking()` — update with correct version succeeds; update with stale version throws `OptimisticLockingFailureException`
- `tenantIsolation()` — findById with wrong tenantId returns empty

**`WorkflowTypeSubmissionJdbcRepositoryTest.java`**:
- `saveAndFindById()`
- `existsByTenantAndWorkflowType_trueForNonRejected()`
- `existsByTenantAndWorkflowType_falseForRejected()`
- `concurrentSave_throwsOptimisticLockingFailure()`
- `deleteById()`

**`AuditEntryJdbcRepositoryTest.java`**:
- `saveAndFindByWorkItem()`
- `idempotentSave()`

**`ConfigDocumentJdbcRepositoryTest.java`**:
- `findActive_returnsLatestVersion()`
- `findAllActive()`

**`SourceConnectionJdbcRepositoryTest.java`**:
- `saveAndFindById()`
- `grantAndRevokeAccess()`
- `findByTenantId()`

**`MultiTenantIsolationTest.java`**:
- Insert work items for tenant-A and tenant-B; verify each tenant only sees their own items

**`OptimisticLockConcurrencyTest.java`**:
- Simulate two concurrent updates: first succeeds, second throws `OptimisticLockingFailureException`

---

## Kafka Integration Test

### `src/test/java/com/platform/api/adapter/in/kafka/KafkaIngestionIntegrationTest.java`
`@SpringBootTest @EmbeddedKafka(partitions = 1, topics = {"work-items.ingest"}) @ActiveProfiles("test")`

Pre-publish a JSON message to `work-items.ingest`. Assert the work item appears in the in-memory store (or verify via GET endpoint) within a timeout. Also test duplicate message produces no second insert.

---

## `src/main/resources/application-test.properties`
```properties
# Use in-memory doubles, no real database
spring.liquibase.enabled=false
spring.kafka.bootstrap-servers=    # empty — disables DomainEventKafkaConfig
```

## Verification
```bash
./gradlew :platform-api:test          # All unit + integration tests pass
./gradlew :platform-api:cucumber      # All BDD scenarios pass
./gradlew :platform-api:build         # Includes JaCoCo coverage check (≥80%)
```
