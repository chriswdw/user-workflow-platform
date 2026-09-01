package com.platform.config.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.config.domain.exception.IncompleteSubmissionException;
import com.platform.config.domain.exception.SelfApprovalException;
import com.platform.config.domain.exception.SubmissionAlreadyExistsException;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.CreateSubmissionCommand;
import com.platform.config.domain.service.SubmissionCreationService;
import com.platform.config.domain.service.SubmissionDraftService;
import com.platform.config.domain.service.SubmissionLifecycleService;
import com.platform.config.domain.service.SubmissionQueryService;
import com.platform.config.domain.service.SubmissionReviewService;
import com.platform.config.doubles.InMemoryConfigDocumentWriter;
import com.platform.config.doubles.InMemorySubmissionAuditRepository;
import com.platform.config.doubles.InMemoryWorkflowTypeSubmissionRepository;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Map;

public class WorkflowTypeSubmissionStepDefinitions {

  private final InMemoryWorkflowTypeSubmissionRepository repo =
      new InMemoryWorkflowTypeSubmissionRepository();
  private final InMemoryConfigDocumentWriter writer = new InMemoryConfigDocumentWriter();
  private final InMemorySubmissionAuditRepository auditRepo =
      new InMemorySubmissionAuditRepository();

  private boolean makerCheckerEnabled = true;

  private SubmissionCreationService creationService() {
    return new SubmissionCreationService(repo, writer, auditRepo, makerCheckerEnabled);
  }

  private SubmissionDraftService draftService() {
    return new SubmissionDraftService(repo, auditRepo);
  }

  private SubmissionLifecycleService lifecycleService() {
    return new SubmissionLifecycleService(repo, auditRepo);
  }

  private SubmissionReviewService reviewService() {
    return new SubmissionReviewService(repo, writer, auditRepo);
  }

  private SubmissionQueryService queryService() {
    return new SubmissionQueryService(repo);
  }

  @Before
  public void resetAll() {
    repo.reset();
    writer.reset();
    auditRepo.reset();
    makerCheckerEnabled = true;
    lastResult = null;
    lastSubmissionId = null;
    originalSubmissionId = null;
    lastDraftConfigs = null;
    listResult = null;
    thrownException = null;
  }

  // Scenario state
  private WorkflowTypeSubmission lastResult;
  private String lastSubmissionId;
  private String originalSubmissionId;
  private DraftConfigs lastDraftConfigs;
  private List<WorkflowTypeSubmission> listResult;
  private Exception thrownException;

  // ── Given ────────────────────────────────────────────────────────────────

  @Given("no submission exists for tenant {string}")
  public void noSubmissionExists(String tenantId) {
    // repo starts empty per scenario — nothing to do
  }

  @Given(
      "a DRAFT submission exists for tenant {string} workflow type {string} submitted by {string}")
  public void draftSubmissionExists(String tenantId, String workflowType, String submittedBy) {
    lastResult =
        creationService()
            .create(
                new CreateSubmissionCommand(
                    tenantId,
                    submittedBy,
                    workflowType,
                    workflowType + " Display",
                    null,
                    incompleteDraftConfigs()));
    lastSubmissionId = lastResult.id();
    originalSubmissionId = lastSubmissionId;
  }

  @Given(
      "a PENDING_APPROVAL submission exists for tenant {string} workflow type {string} submitted by {string}")
  public void pendingSubmissionExists(String tenantId, String workflowType, String submittedBy) {
    draftSubmissionExists(tenantId, workflowType, submittedBy);
    setCompleteConfigs(tenantId, submittedBy);
    lastResult = lifecycleService().submit(tenantId, lastSubmissionId, submittedBy);
  }

  @Given(
      "a REJECTED submission exists for tenant {string} workflow type {string} submitted by {string}")
  public void rejectedSubmissionExists(String tenantId, String workflowType, String submittedBy) {
    pendingSubmissionExists(tenantId, workflowType, submittedBy);
    lastResult = reviewService().reject(tenantId, lastSubmissionId, "bob", "Test rejection");
  }

  @Given(
      "an APPROVED submission exists for tenant {string} workflow type {string} submitted by {string}")
  public void approvedSubmissionExists(String tenantId, String workflowType, String submittedBy) {
    pendingSubmissionExists(tenantId, workflowType, submittedBy);
    lastResult = reviewService().approve(tenantId, lastSubmissionId, "bob");
  }

  @Given("the submission has complete draft configs")
  public void submissionHasCompleteDraftConfigs() {
    lastResult =
        draftService()
            .saveDraft(
                lastResult.tenantId(),
                lastSubmissionId,
                lastResult.submittedBy(),
                completeDraftConfigs(),
                lastResult.currentStep());
  }

  @Given("the submission has incomplete draft configs")
  public void submissionHasIncompleteDraftConfigs() {
    // already incomplete from creation — nothing to do
  }

  @Given("maker-checker is disabled")
  public void makerCheckerIsDisabled() {
    makerCheckerEnabled = false;
  }

  // ── When ─────────────────────────────────────────────────────────────────

  @When("user {string} creates a submission for workflow type {string} with display name {string}")
  public void userCreatesSubmission(String userId, String workflowType, String displayName) {
    try {
      lastResult =
          creationService()
              .create(
                  new CreateSubmissionCommand(
                      "tenant-1", userId, workflowType, displayName, null, completeDraftConfigs()));
      lastSubmissionId = lastResult.id();
      originalSubmissionId = lastSubmissionId;
    } catch (Exception e) {
      thrownException = e;
    }
  }

  @When("user {string} submits the submission for approval")
  public void userSubmitsForApproval(String userId) {
    try {
      lastResult = lifecycleService().submit(lastResult.tenantId(), lastSubmissionId, userId);
    } catch (Exception e) {
      thrownException = e;
    }
  }

  @When("user {string} approves the submission")
  public void userApprovesSubmission(String userId) {
    try {
      lastResult = reviewService().approve(lastResult.tenantId(), lastSubmissionId, userId);
    } catch (Exception e) {
      thrownException = e;
    }
  }

  @When("user {string} rejects the submission with reason {string}")
  public void userRejectsSubmission(String userId, String reason) {
    try {
      lastResult = reviewService().reject(lastResult.tenantId(), lastSubmissionId, userId, reason);
    } catch (Exception e) {
      thrownException = e;
    }
  }

  @When("user {string} saves draft progress at step {int}")
  public void userSavesDraftProgress(String userId, int step) {
    try {
      lastResult =
          draftService()
              .saveDraft(
                  lastResult.tenantId(), lastSubmissionId, userId, lastResult.draftConfigs(), step);
    } catch (Exception e) {
      thrownException = e;
    }
  }

  @When("user {string} discards the submission as owner")
  public void userDiscardsAsOwner(String userId) {
    try {
      draftService().discard(lastResult.tenantId(), lastSubmissionId, userId, false);
    } catch (Exception e) {
      thrownException = e;
    }
  }

  @When("user {string} discards the submission as admin")
  public void userDiscardsAsAdmin(String userId) {
    try {
      draftService().discard(lastResult.tenantId(), lastSubmissionId, userId, true);
    } catch (Exception e) {
      thrownException = e;
    }
  }

  @When("user {string} revises the submission with updated draft configs")
  public void userRevisesSubmission(String userId) {
    lastDraftConfigs = completeDraftConfigs();
    try {
      lastResult =
          lifecycleService()
              .revise(lastResult.tenantId(), lastSubmissionId, userId, lastDraftConfigs);
    } catch (Exception e) {
      thrownException = e;
    }
  }

  @When("the pending submissions for tenant {string} are retrieved")
  public void pendingSubmissionsRetrieved(String tenantId) {
    listResult = queryService().getPendingForTenant(tenantId);
  }

  @When("the draft submissions for user {string} in tenant {string} are retrieved")
  public void draftSubmissionsRetrieved(String userId, String tenantId) {
    listResult = queryService().getDraftsForUser(tenantId, userId);
  }

  @And("the submission is loaded by id")
  public void submissionLoadedById() {
    lastResult = queryService().getById(lastResult.tenantId(), lastSubmissionId);
  }

  // ── Then ─────────────────────────────────────────────────────────────────

  @Then("the submission status is {string}")
  public void submissionStatusIs(String expectedStatus) {
    assertThat(lastResult.status().name()).isEqualTo(expectedStatus);
  }

  @Then("the submission workflow type is {string}")
  public void submissionWorkflowTypeIs(String expectedType) {
    assertThat(lastResult.workflowType()).isEqualTo(expectedType);
  }

  @Then("the submission submitted by is {string}")
  public void submissionSubmittedByIs(String expectedUser) {
    assertThat(lastResult.submittedBy()).isEqualTo(expectedUser);
  }

  @Then("the submission current step is {int}")
  public void submissionCurrentStepIs(int expectedStep) {
    assertThat(lastResult.currentStep()).isEqualTo(expectedStep);
  }

  @Then("the submission submitted at is set")
  public void submissionSubmittedAtIsSet() {
    assertThat(lastResult.submittedAt()).isNotNull();
  }

  @Then("{int} config documents have been published")
  public void configDocumentsPublished(int expectedCount) {
    assertThat(writer.getAll()).hasSize(expectedCount);
  }

  @Then("the rejection reason is {string}")
  public void rejectionReasonIs(String expectedReason) {
    assertThat(lastResult.rejectionReason()).isEqualTo(expectedReason);
  }

  @Then("the rejection reason is null")
  public void rejectionReasonIsNull() {
    assertThat(lastResult.rejectionReason()).isNull();
  }

  @Then("the reviewed by is {string}")
  public void reviewedByIs(String expectedUser) {
    assertThat(lastResult.reviewedBy()).isEqualTo(expectedUser);
  }

  @Then("the pending submissions list is empty")
  public void pendingListIsEmpty() {
    assertThat(listResult).isEmpty();
  }

  @Then("the pending submissions list contains {int} submission")
  public void pendingListContains(int expectedCount) {
    assertThat(listResult).hasSize(expectedCount);
  }

  @Then("the draft submissions list contains {int} submission")
  public void draftListContains(int expectedCount) {
    assertThat(listResult).hasSize(expectedCount);
  }

  @Then("the submission id is unchanged")
  public void submissionIdUnchanged() {
    assertThat(lastResult.id()).isEqualTo(originalSubmissionId);
  }

  @Then("the draft configs have been updated")
  public void draftConfigsUpdated() {
    assertThat(lastResult.draftConfigs()).isEqualTo(lastDraftConfigs);
  }

  @Then("a SubmissionAlreadyExistsException is thrown")
  public void submissionAlreadyExistsExceptionThrown() {
    assertThat(thrownException).isInstanceOf(SubmissionAlreadyExistsException.class);
  }

  @Then("a SelfApprovalException is thrown")
  public void selfApprovalExceptionThrown() {
    assertThat(thrownException).isInstanceOf(SelfApprovalException.class);
  }

  @Then("an IncompleteSubmissionException is thrown")
  public void incompleteSubmissionExceptionThrown() {
    assertThat(thrownException).isInstanceOf(IncompleteSubmissionException.class);
  }

  @Then("an IllegalStateException is thrown")
  public void illegalStateExceptionThrown() {
    assertThat(thrownException).isInstanceOf(IllegalStateException.class);
  }

  @Then("an IllegalArgumentException is thrown")
  public void illegalArgumentExceptionThrown() {
    assertThat(thrownException).isInstanceOf(IllegalArgumentException.class);
  }

  @Then("the submission is deleted")
  public void submissionIsDeleted() {
    assertThat(repo.findById(lastResult.tenantId(), lastSubmissionId)).isEmpty();
  }

  @Then("a SUBMISSION_DISCARDED audit entry is recorded")
  public void submissionDiscardedAuditEntryRecorded() {
    assertThat(auditRepo.findByEventType(AuditEventType.SUBMISSION_DISCARDED)).isNotEmpty();
  }

  @Then("an audit entry of type {string} is recorded for the submission")
  public void auditEntryRecorded(String eventTypeName) {
    AuditEventType type = AuditEventType.valueOf(eventTypeName);
    assertThat(auditRepo.findByEventType(type)).isNotEmpty();
  }

  @Then("the audit entry records actor {string}")
  public void auditEntryRecordsActor(String userId) {
    AuditEntry latest =
        auditRepo.findBySubmissionId(lastSubmissionId).stream().reduce((a, b) -> b).orElseThrow();
    assertThat(latest.actorUserId()).isEqualTo(userId);
  }

  @Then("the audit entry records previous state {string} and new state {string}")
  public void auditEntryRecordsStates(String prev, String next) {
    AuditEntry latest =
        auditRepo.findBySubmissionId(lastSubmissionId).stream().reduce((a, b) -> b).orElseThrow();
    assertThat(latest.previousState()).isEqualTo("null".equals(prev) ? null : prev);
    assertThat(latest.newState()).isEqualTo("null".equals(next) ? null : next);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void setCompleteConfigs(String tenantId, String submittedBy) {
    draftService().saveDraft(tenantId, lastSubmissionId, submittedBy, completeDraftConfigs(), 6);
    lastResult = repo.findById(tenantId, lastSubmissionId).orElseThrow();
  }

  private static DraftConfigs incompleteDraftConfigs() {
    Map<String, Object> basic = Map.of("workflowType", "TRADE_BREAK");
    return new DraftConfigs(basic, basic, basic, basic, null, null);
  }

  private static DraftConfigs completeDraftConfigs() {
    Map<String, Object> basic = Map.of("workflowType", "TRADE_BREAK");
    Map<String, Object> blotter =
        Map.of("columns", List.of(Map.of("field", "trade.ref", "header", "Ref")));
    Map<String, Object> detail =
        Map.of(
            "sections",
            List.of(Map.of("title", "Details", "fields", List.of(Map.of("field", "trade.ref")))));
    return new DraftConfigs(basic, basic, basic, basic, blotter, detail);
  }
}
