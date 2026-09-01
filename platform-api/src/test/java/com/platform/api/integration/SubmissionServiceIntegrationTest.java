package com.platform.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.api.adapter.out.postgres.AuditEntryJdbcRepository;
import com.platform.api.adapter.out.postgres.ConfigDocumentJdbcWriter;
import com.platform.api.adapter.out.postgres.EmbeddedPostgresProvider;
import com.platform.api.adapter.out.postgres.WorkflowTypeSubmissionJdbcRepository;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.CreateSubmissionCommand;
import com.platform.config.domain.service.SubmissionCreationService;
import com.platform.config.domain.service.SubmissionDraftService;
import com.platform.config.domain.service.SubmissionLifecycleService;
import com.platform.config.domain.service.SubmissionReviewService;
import com.platform.domain.model.AuditEventType;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SubmissionServiceIntegrationTest {

  private static final String TENANT = "tenant-1";
  private static final String WORKFLOW_TYPE = "TRADE_BREAK";

  private static final NamedParameterJdbcTemplate jdbc =
      new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
  private static final ObjectMapper objectMapper = new JsonMapper();

  private final WorkflowTypeSubmissionJdbcRepository submissionRepo =
      new WorkflowTypeSubmissionJdbcRepository(jdbc, objectMapper);
  private final AuditEntryJdbcRepository auditRepo =
      new AuditEntryJdbcRepository(jdbc, objectMapper);
  private final ConfigDocumentJdbcWriter configWriter =
      new ConfigDocumentJdbcWriter(jdbc, objectMapper);

  private SubmissionCreationService creationService() {
    return new SubmissionCreationService(submissionRepo, configWriter, auditRepo, true);
  }

  private SubmissionDraftService draftService() {
    return new SubmissionDraftService(submissionRepo, auditRepo);
  }

  private SubmissionLifecycleService lifecycleService() {
    return new SubmissionLifecycleService(submissionRepo, auditRepo);
  }

  private SubmissionReviewService reviewService() {
    return new SubmissionReviewService(submissionRepo, configWriter, auditRepo);
  }

  @BeforeEach
  void truncate() {
    jdbc.update("TRUNCATE workflow_type_submissions CASCADE", Map.of());
    jdbc.update("TRUNCATE audit_entries", Map.of());
  }

  @Test
  void discard_draft_byOwner_removesFromDatabase() {
    WorkflowTypeSubmission created = creationService().create(createCommand("alice"));

    draftService().discard(TENANT, created.id(), "alice", false);

    assertThat(submissionRepo.findById(TENANT, created.id())).isEmpty();
  }

  @Test
  void discard_draft_byAdmin_removesFromDatabase() {
    WorkflowTypeSubmission created = creationService().create(createCommand("alice"));

    draftService().discard(TENANT, created.id(), "bob", true);

    assertThat(submissionRepo.findById(TENANT, created.id())).isEmpty();
  }

  @Test
  void discard_rejected_byOwner_removesFromDatabase() {
    WorkflowTypeSubmission created = creationService().create(completeCreateCommand("alice"));
    WorkflowTypeSubmission submitted = lifecycleService().submit(TENANT, created.id(), "alice");
    reviewService().reject(TENANT, submitted.id(), "bob", "Needs revision");

    draftService().discard(TENANT, created.id(), "alice", false);

    assertThat(submissionRepo.findById(TENANT, created.id())).isEmpty();
  }

  @Test
  void discard_recordsSubmissionDiscardedAuditEntry() {
    WorkflowTypeSubmission created = creationService().create(createCommand("alice"));

    draftService().discard(TENANT, created.id(), "alice", false);

    var entries = auditRepo.findByTenantAndWorkItemId(TENANT, created.id());
    assertThat(entries).anyMatch(e -> e.eventType() == AuditEventType.SUBMISSION_DISCARDED);
    var discardEntry =
        entries.stream()
            .filter(e -> e.eventType() == AuditEventType.SUBMISSION_DISCARDED)
            .findFirst()
            .orElseThrow();
    assertThat(discardEntry.actorUserId()).isEqualTo("alice");
    assertThat(discardEntry.previousState()).isEqualTo(SubmissionStatus.DRAFT.name());
    assertThat(discardEntry.newState()).isEqualTo("DISCARDED");
  }

  @Test
  void discard_pendingApproval_throwsIllegalStateException() {
    WorkflowTypeSubmission created = creationService().create(completeCreateCommand("alice"));
    lifecycleService().submit(TENANT, created.id(), "alice");
    var draftService = draftService();
    var submissionId = created.id();

    assertThatThrownBy(() -> draftService.discard(TENANT, submissionId, "alice", false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SubmissionStatus.PENDING_APPROVAL.name());
  }

  @Test
  void discard_nonOwnerNonAdmin_throwsIllegalStateException() {
    WorkflowTypeSubmission created = creationService().create(createCommand("alice"));
    var draftService = draftService();
    var submissionId = created.id();

    assertThatThrownBy(() -> draftService.discard(TENANT, submissionId, "charlie", false))
        .isInstanceOf(IllegalStateException.class);
  }

  private static CreateSubmissionCommand createCommand(String userId) {
    DraftConfigs configs =
        new DraftConfigs(
            Map.of("workflowType", WORKFLOW_TYPE),
            Map.of("workflowType", WORKFLOW_TYPE),
            Map.of("workflowType", WORKFLOW_TYPE),
            Map.of("workflowType", WORKFLOW_TYPE),
            null,
            null);
    return new CreateSubmissionCommand(
        TENANT, userId, WORKFLOW_TYPE, "Trade Break", "desc", configs);
  }

  private static CreateSubmissionCommand completeCreateCommand(String userId) {
    Map<String, Object> basic = Map.of("workflowType", WORKFLOW_TYPE);
    Map<String, Object> blotter =
        Map.of("columns", java.util.List.of(Map.of("field", "trade.ref", "header", "Ref")));
    Map<String, Object> detail =
        Map.of(
            "sections",
            java.util.List.of(
                Map.of(
                    "title",
                    "Details",
                    "fields",
                    java.util.List.of(Map.of("field", "trade.ref")))));
    DraftConfigs configs = new DraftConfigs(basic, basic, basic, basic, blotter, detail);
    return new CreateSubmissionCommand(
        TENANT, userId, WORKFLOW_TYPE, "Trade Break", "desc", configs);
  }
}
