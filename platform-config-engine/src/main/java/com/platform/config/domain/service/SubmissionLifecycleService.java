package com.platform.config.domain.service;

import com.platform.config.domain.exception.IncompleteSubmissionException;
import com.platform.config.domain.exception.SubmissionNotFoundException;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.IReviseSubmissionUseCase;
import com.platform.config.domain.ports.in.ISubmitForApprovalUseCase;
import com.platform.domain.model.AuditEventType;
import com.platform.domain.ports.out.IAuditRepository;
import com.platform.config.domain.ports.out.IWorkflowTypeSubmissionRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class SubmissionLifecycleService implements ISubmitForApprovalUseCase, IReviseSubmissionUseCase {

    private static final String DISPLAY_NAME_DRAFT = "Draft";

    private final IWorkflowTypeSubmissionRepository repo;
    private final IAuditRepository auditRepo;

    public SubmissionLifecycleService(IWorkflowTypeSubmissionRepository repo,
                                       IAuditRepository auditRepo) {
        this.repo = repo;
        this.auditRepo = auditRepo;
    }

    @Override
    public WorkflowTypeSubmission submit(String tenantId, String submissionId, String actorUserId) {
        WorkflowTypeSubmission submission = load(tenantId, submissionId);
        SubmissionGuards.assertStatus(submission, SubmissionStatus.DRAFT, "submit");
        SubmissionGuards.assertOwner(submission, actorUserId);

        if (!submission.draftConfigs().isComplete()) {
            throw new IncompleteSubmissionException(
                    "blotterConfig and detailViewConfig must both contain at least one entry");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        WorkflowTypeSubmission saved = repo.save(new WorkflowTypeSubmission(
                submission.id(), submission.tenantId(), submission.workflowType(),
                submission.displayName(), submission.description(),
                SubmissionStatus.PENDING_APPROVAL, "Pending Approval",
                submission.draftConfigs(),
                submission.submittedBy(), now,
                null, null, null,
                submission.currentStep(),
                submission.version() + 1,
                submission.createdAt(), now));
        auditRepo.save(SubmissionGuards.submissionAuditEntry(saved, AuditEventType.SUBMISSION_SUBMITTED_FOR_REVIEW,
                "DRAFT", "PENDING_APPROVAL", actorUserId));
        return saved;
    }

    @Override
    public WorkflowTypeSubmission revise(String tenantId, String submissionId,
                                          String actorUserId, DraftConfigs updatedDraftConfigs) {
        WorkflowTypeSubmission submission = load(tenantId, submissionId);
        SubmissionGuards.assertStatus(submission, SubmissionStatus.REJECTED, "revise");
        SubmissionGuards.assertOwner(submission, actorUserId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        WorkflowTypeSubmission saved = repo.save(new WorkflowTypeSubmission(
                submission.id(), submission.tenantId(), submission.workflowType(),
                submission.displayName(), submission.description(),
                SubmissionStatus.DRAFT, DISPLAY_NAME_DRAFT,
                updatedDraftConfigs,
                submission.submittedBy(), null,
                null, null, null,
                1,
                submission.version() + 1,
                submission.createdAt(), now));
        auditRepo.save(SubmissionGuards.submissionAuditEntry(saved, AuditEventType.SUBMISSION_REVISED,
                "REJECTED", "DRAFT", actorUserId));
        return saved;
    }

    private WorkflowTypeSubmission load(String tenantId, String submissionId) {
        return repo.findById(tenantId, submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
    }
}
