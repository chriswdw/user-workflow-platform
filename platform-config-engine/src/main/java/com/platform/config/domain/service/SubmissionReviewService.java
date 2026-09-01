package com.platform.config.domain.service;

import com.platform.config.domain.exception.SubmissionNotFoundException;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.IReviewSubmissionUseCase;
import com.platform.config.domain.ports.out.IConfigDocumentWriter;
import com.platform.config.domain.ports.out.IWorkflowTypeSubmissionRepository;
import com.platform.domain.model.AuditEventType;
import com.platform.domain.ports.out.IAuditRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class SubmissionReviewService implements IReviewSubmissionUseCase {

  private final IWorkflowTypeSubmissionRepository repo;
  private final IConfigDocumentWriter configDocumentWriter;
  private final IAuditRepository auditRepo;

  public SubmissionReviewService(
      IWorkflowTypeSubmissionRepository repo,
      IConfigDocumentWriter configDocumentWriter,
      IAuditRepository auditRepo) {
    this.repo = repo;
    this.configDocumentWriter = configDocumentWriter;
    this.auditRepo = auditRepo;
  }

  @Override
  public WorkflowTypeSubmission approve(
      String tenantId, String submissionId, String reviewerUserId) {
    WorkflowTypeSubmission submission = load(tenantId, submissionId);
    SubmissionGuards.assertStatus(submission, SubmissionStatus.PENDING_APPROVAL, "approve");
    SubmissionGuards.assertNotSelfApproval(submission, reviewerUserId);

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
                submission.submittedAt(),
                reviewerUserId,
                now,
                null,
                submission.currentStep(),
                submission.version() + 1,
                submission.createdAt(),
                now));
    auditRepo.save(
        SubmissionGuards.submissionAuditEntry(
            saved,
            AuditEventType.SUBMISSION_APPROVED,
            "PENDING_APPROVAL",
            "APPROVED",
            reviewerUserId));
    return saved;
  }

  @Override
  public WorkflowTypeSubmission reject(
      String tenantId, String submissionId, String reviewerUserId, String reason) {
    WorkflowTypeSubmission submission = load(tenantId, submissionId);
    SubmissionGuards.assertStatus(submission, SubmissionStatus.PENDING_APPROVAL, "reject");
    SubmissionGuards.assertNotSelfApproval(submission, reviewerUserId);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    WorkflowTypeSubmission saved =
        repo.save(
            new WorkflowTypeSubmission(
                submission.id(),
                submission.tenantId(),
                submission.workflowType(),
                submission.displayName(),
                submission.description(),
                SubmissionStatus.REJECTED,
                "Rejected",
                submission.draftConfigs(),
                submission.submittedBy(),
                submission.submittedAt(),
                reviewerUserId,
                now,
                reason,
                submission.currentStep(),
                submission.version() + 1,
                submission.createdAt(),
                now));
    auditRepo.save(
        SubmissionGuards.submissionAuditEntry(
            saved,
            AuditEventType.SUBMISSION_REJECTED,
            "PENDING_APPROVAL",
            "REJECTED",
            reviewerUserId));
    return saved;
  }

  private WorkflowTypeSubmission load(String tenantId, String submissionId) {
    return repo.findById(tenantId, submissionId)
        .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
  }
}
