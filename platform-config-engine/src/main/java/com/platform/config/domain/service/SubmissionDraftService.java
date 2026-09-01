package com.platform.config.domain.service;

import com.platform.config.domain.exception.SubmissionNotFoundException;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.IDiscardSubmissionUseCase;
import com.platform.config.domain.ports.in.ISaveDraftUseCase;
import com.platform.domain.model.AuditEventType;
import com.platform.domain.ports.out.IAuditRepository;
import com.platform.config.domain.ports.out.IWorkflowTypeSubmissionRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class SubmissionDraftService implements ISaveDraftUseCase, IDiscardSubmissionUseCase {

    private final IWorkflowTypeSubmissionRepository repo;
    private final IAuditRepository auditRepo;

    public SubmissionDraftService(IWorkflowTypeSubmissionRepository repo,
                                   IAuditRepository auditRepo) {
        this.repo = repo;
        this.auditRepo = auditRepo;
    }

    @Override
    public WorkflowTypeSubmission saveDraft(String tenantId, String submissionId,
                                             String actorUserId,
                                             DraftConfigs partialDraftConfigs,
                                             int currentStep) {
        WorkflowTypeSubmission submission = load(tenantId, submissionId);
        SubmissionGuards.assertStatus(submission, SubmissionStatus.DRAFT, "saveDraft");
        SubmissionGuards.assertOwner(submission, actorUserId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repo.save(new WorkflowTypeSubmission(
                submission.id(), submission.tenantId(), submission.workflowType(),
                submission.displayName(), submission.description(),
                submission.status(), submission.statusDisplayName(),
                partialDraftConfigs,
                submission.submittedBy(), submission.submittedAt(),
                submission.reviewedBy(), submission.reviewedAt(),
                submission.rejectionReason(),
                currentStep,
                submission.version() + 1,
                submission.createdAt(), now));
    }

    @Override
    public void discard(String tenantId, String submissionId, String actorUserId, boolean isAdmin) {
        WorkflowTypeSubmission submission = load(tenantId, submissionId);
        if (submission.status() != SubmissionStatus.DRAFT && submission.status() != SubmissionStatus.REJECTED) {
            throw new IllegalStateException(
                    "Cannot discard a submission in status " + submission.status()
                    + "; only DRAFT or REJECTED submissions may be discarded");
        }
        if (!isAdmin) {
            SubmissionGuards.assertOwner(submission, actorUserId);
        }
        repo.deleteById(tenantId, submissionId);
        auditRepo.save(SubmissionGuards.submissionAuditEntry(submission, AuditEventType.SUBMISSION_DISCARDED,
                submission.status().name(), "DISCARDED", actorUserId));
    }

    private WorkflowTypeSubmission load(String tenantId, String submissionId) {
        return repo.findById(tenantId, submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
    }
}
