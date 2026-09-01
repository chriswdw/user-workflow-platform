# Prompt 08 — platform-config-engine testFixtures + BDD

## Goal
Add the four in-memory test doubles to the `testFixtures` source set, then write the Cucumber BDD tests for the config-engine domain. These test doubles will be reused by `platform-api` tests. The BDD scenarios must **fail (RED)** before the domain services from Prompt 07 are in place, and pass (GREEN) after.

## testFixtures source set
All four files go in: `platform-config-engine/src/testFixtures/java/com/platform/config/doubles/`

### `InMemoryWorkflowTypeSubmissionRepository.java`
Implements `IWorkflowTypeSubmissionRepository` (from Prompt 07). Backed by `Map<String, WorkflowTypeSubmission>` keyed on `tenantId + ":" + id`.
- `save(submission)`: put and return
- `findById(tenantId, id)`: lookup by composite key
- `existsByTenantAndWorkflowType(tenantId, workflowType)`: scan for any non-REJECTED match
- `findPendingByTenant(tenantId)`: filter by tenant + PENDING_APPROVAL
- `findDraftsByUser(tenantId, userId)`: filter by tenant + DRAFT + submittedBy
- `findRejectedByUser(tenantId, userId)`: filter by tenant + REJECTED + submittedBy
- `findAllDraftsByTenant(tenantId)`: filter by tenant + DRAFT
- `delete(tenantId, id)`: remove from map
- Expose `getAll()` for test assertions

### `InMemoryConfigDocumentRepository.java`
Implements `IConfigDocumentRepository`. Backed by `List<ConfigDocument>`.
- `findActive(tenantId, workflowType, configType)`: filter by all three + `active=true`, return last
- `findAllActive(tenantId, workflowType)`: filter by tenant + workflowType + active

### `InMemoryConfigDocumentWriter.java`
Implements `IConfigDocumentWriter`. Backed by `List<ConfigDocument>`.
- `saveAll(documents)`: add all to list
- Expose `getAll()` for test assertions

### `InMemorySubmissionAuditRepository.java`
Implements `IAuditRepository`. Backed by `List<AuditEntry>`. Expose `getAll()`.

## BDD test files

### `src/test/java/com/platform/config/CucumberSuiteTest.java`
```java
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/config")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
    value = "com.platform.config.steps")
public class CucumberSuiteTest {}
```

### `src/test/resources/features/config/workflow-type-submission.feature`
```gherkin
Feature: Workflow type submission lifecycle

  Scenario: Create a new workflow type submission as DRAFT
    Given a tenant "tenant-1" and user "alice"
    When Alice creates a submission for workflow type "TRADE_SETTLEMENT"
    Then the submission status is "DRAFT"
    And an audit entry of type SUBMISSION_CREATED is recorded

  Scenario: Cannot create duplicate active submission
    Given a DRAFT submission exists for workflow type "TRADE_SETTLEMENT" under tenant "tenant-1"
    When Alice creates another submission for workflow type "TRADE_SETTLEMENT"
    Then a SubmissionAlreadyExistsException is thrown

  Scenario: Submit for approval
    Given a DRAFT submission with complete draftConfigs
    When Alice submits the submission for approval
    Then the submission status is "PENDING_APPROVAL"
    And an audit entry of type SUBMISSION_SUBMITTED_FOR_REVIEW is recorded

  Scenario: Cannot submit incomplete draft
    Given a DRAFT submission with empty blotterConfig
    When Alice submits the submission for approval
    Then an IncompleteSubmissionException is thrown

  Scenario: Approve a pending submission
    Given a PENDING_APPROVAL submission submitted by "alice"
    When reviewer "bob" approves the submission
    Then the submission status is "APPROVED"
    And an audit entry of type SUBMISSION_APPROVED is recorded
    And config documents are published

  Scenario: Self-approval is rejected
    Given a PENDING_APPROVAL submission submitted by "alice"
    When "alice" tries to approve the submission
    Then a SelfApprovalException is thrown

  Scenario: Reject a pending submission
    Given a PENDING_APPROVAL submission submitted by "alice"
    When reviewer "bob" rejects the submission with reason "Missing risk config"
    Then the submission status is "REJECTED"
    And the rejection reason is "Missing risk config"
    And an audit entry of type SUBMISSION_REJECTED is recorded

  Scenario: Revise a rejected submission
    Given a REJECTED submission submitted by "alice"
    When Alice revises the submission with updated configs
    Then the submission status is "DRAFT"
    And an audit entry of type SUBMISSION_REVISED is recorded

  Scenario: Discard a DRAFT submission
    Given a DRAFT submission submitted by "alice"
    When Alice discards the submission
    Then the submission no longer exists
    And an audit entry of type SUBMISSION_DISCARDED is recorded

  Scenario: Admin can discard any submission
    Given a PENDING_APPROVAL submission submitted by "alice"
    When admin "carol" discards the submission
    Then the submission no longer exists

  Scenario: workflow type must match ^[A-Z][A-Z0-9_]*$
    When Alice creates a submission for workflow type "invalid-type"
    Then an IllegalArgumentException is thrown
```

### `src/test/resources/features/config/config-documents.feature`
```gherkin
Feature: Config document management

  Scenario: Load active config document
    Given a config document of type BLOTTER_CONFIG exists for workflow type "SETTLEMENT_EXCEPTION"
    When loading BLOTTER_CONFIG for "SETTLEMENT_EXCEPTION"
    Then the document is returned

  Scenario: ConfigNotFoundException when no active document
    When loading BLOTTER_CONFIG for "UNKNOWN_TYPE"
    Then a ConfigNotFoundException is thrown
```

### `src/test/java/com/platform/config/steps/WorkflowTypeSubmissionStepDefinitions.java`
Spring-free step defs. In `@Before` hook, wire all services with in-memory doubles:
```java
repo = new InMemoryWorkflowTypeSubmissionRepository();
writer = new InMemoryConfigDocumentWriter();
auditRepo = new InMemorySubmissionAuditRepository();
creationService = new SubmissionCreationService(repo, writer, auditRepo, true);
draftService = new SubmissionDraftService(repo, auditRepo);
lifecycleService = new SubmissionLifecycleService(repo, auditRepo);
reviewService = new SubmissionReviewService(repo, writer, auditRepo);
queryService = new SubmissionQueryService(repo);
discardService = new DiscardSubmissionService(repo, auditRepo);
```
Build `DraftConfigs` with non-empty blotterConfig and detailViewConfig for "complete" scenarios; empty maps for "incomplete" scenarios. Catch and store exceptions in a `Throwable lastException` field. Assert in `@Then` steps.

### `src/test/java/com/platform/config/steps/ConfigStepDefinitions.java`
Spring-free step defs for config document scenarios. Wire `ConfigService` with `InMemoryConfigDocumentRepository` and `SimpleMeterRegistry`. Pre-populate repository in `@Given` steps.

## Constraints
- testFixtures classes must be in `src/testFixtures/java/` — NOT in `src/test/java/`
- testFixtures classes can only import from `platform-domain` (already in `testFixturesImplementation`)
- The `@Suite`, `@IncludeEngines`, `@SelectClasspathResource`, `@ConfigurationParameter` annotations are from `org.junit.platform.suite.api` and `io.cucumber.junit.platform.engine.Constants`
- BDD step defs never use `@SpringBootTest` or `@ContextConfiguration`

## Verification
```bash
./gradlew :platform-config-engine:cucumber   # All scenarios GREEN
```
