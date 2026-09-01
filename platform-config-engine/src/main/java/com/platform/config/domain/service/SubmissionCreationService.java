package com.platform.config.domain.service;

import com.platform.config.domain.exception.SubmissionAlreadyExistsException;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.CreateSubmissionCommand;
import com.platform.config.domain.ports.in.ICreateWorkflowTypeSubmissionUseCase;
import com.platform.config.domain.ports.out.IConfigDocumentWriter;
import com.platform.config.domain.ports.out.IWorkflowTypeSubmissionRepository;
import com.platform.domain.model.AuditEventType;
import com.platform.domain.ports.out.IAuditRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;

public class SubmissionCreationService implements ICreateWorkflowTypeSubmissionUseCase {

  private static final Pattern WORKFLOW_TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
  private static final String DISPLAY_NAME_DRAFT = "Draft";

  private final IWorkflowTypeSubmissionRepository repo;
  private final IConfigDocumentWriter configDocumentWriter;
  private final IAuditRepository auditRepo;
  private final boolean makerCheckerEnabled;

  public SubmissionCreationService(
      IWorkflowTypeSubmissionRepository repo,
      IConfigDocumentWriter configDocumentWriter,
      IAuditRepository auditRepo,
      boolean makerCheckerEnabled) {
    this.repo = repo;
    this.configDocumentWriter = configDocumentWriter;
    this.auditRepo = auditRepo;
    this.makerCheckerEnabled = makerCheckerEnabled;
  }

  @Override
  public WorkflowTypeSubmission create(CreateSubmissionCommand command) {
    if (!WORKFLOW_TYPE_PATTERN.matcher(command.workflowType()).matches()) {
      throw new IllegalArgumentException(
          "workflowType must match ^[A-Z][A-Z0-9_]*$, got: " + command.workflowType());
    }
    if (repo.existsByTenantAndWorkflowType(command.tenantId(), command.workflowType())) {
      throw new SubmissionAlreadyExistsException(command.workflowType());
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    WorkflowTypeSubmission submission =
        new WorkflowTypeSubmission(
            UUID.randomUUID().toString(),
            command.tenantId(),
            command.workflowType(),
            command.displayName(),
            command.description(),
            SubmissionStatus.DRAFT,
            DISPLAY_NAME_DRAFT,
            command.draftConfigs(),
            command.actorUserId(),
            null,
            null,
            null,
            null,
            1,
            1,
            now,
            now);

    WorkflowTypeSubmission saved = repo.save(submission);
    auditRepo.save(
        SubmissionGuards.submissionAuditEntry(
            saved, AuditEventType.SUBMISSION_CREATED, null, "DRAFT", command.actorUserId()));

    if (!makerCheckerEnabled) {
      return autoApprove(saved, command.actorUserId());
    }
    return saved;
  }

  private WorkflowTypeSubmission autoApprove(
      WorkflowTypeSubmission submission, String actorUserId) {
    SubmissionGuards.publishConfigDocuments(submission, configDocumentWriter);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    WorkflowTypeSubmission saved =
        repo.save(
            new WorkflowTypeSubmission(
                submission.id(),
                submission.tenantId(),
                submission.workflowType(),
                submission.displayName(),
                submission.description(),
                SubmissionStatus.APPROVED,
                "Approved",
                submission.draftConfigs(),
                submission.submittedBy(),
                now,
                actorUserId,
                now,
                null,
                submission.currentStep(),
                submission.version() + 1,
                submission.createdAt(),
                now));
    auditRepo.save(
        SubmissionGuards.submissionAuditEntry(
            saved, AuditEventType.SUBMISSION_APPROVED, "DRAFT", "APPROVED", actorUserId));
    return saved;
  }
}
