package com.platform.api.doubles;

import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.CreateSubmissionCommand;
import com.platform.config.domain.ports.in.ICreateWorkflowTypeSubmissionUseCase;
import com.platform.config.domain.ports.in.IGetSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviewSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviseSubmissionUseCase;
import com.platform.config.domain.ports.in.ISaveDraftUseCase;
import com.platform.config.domain.ports.in.ISubmitForApprovalUseCase;
import com.platform.config.domain.service.WorkflowTypeSubmissionService;
import com.platform.config.doubles.InMemoryConfigDocumentWriter;
import com.platform.config.doubles.InMemorySubmissionAuditRepository;
import com.platform.config.doubles.InMemoryWorkflowTypeSubmissionRepository;

import java.util.List;

public class InMemorySubmissionPort
        implements ICreateWorkflowTypeSubmissionUseCase,
                   ISaveDraftUseCase,
                   ISubmitForApprovalUseCase,
                   IReviewSubmissionUseCase,
                   IReviseSubmissionUseCase,
                   IGetSubmissionUseCase {

    private final InMemoryWorkflowTypeSubmissionRepository repo =
            new InMemoryWorkflowTypeSubmissionRepository();
    private final InMemorySubmissionAuditRepository auditRepo =
            new InMemorySubmissionAuditRepository();
    private final InMemoryConfigDocumentWriter writer =
            new InMemoryConfigDocumentWriter();

    private WorkflowTypeSubmissionService service() {
        return new WorkflowTypeSubmissionService(repo, writer, auditRepo, true);
    }

    public void seed(WorkflowTypeSubmission submission) {
        repo.save(submission);
    }

    public void reset() {
        repo.reset();
        auditRepo.reset();
        writer.reset();
    }

    @Override
    public WorkflowTypeSubmission create(CreateSubmissionCommand command) {
        return service().create(command);
    }

    @Override
    public WorkflowTypeSubmission saveDraft(String tenantId, String submissionId,
                                             String actorUserId, DraftConfigs partialDraftConfigs, int currentStep) {
        return service().saveDraft(tenantId, submissionId, actorUserId, partialDraftConfigs, currentStep);
    }

    @Override
    public WorkflowTypeSubmission submit(String tenantId, String submissionId, String actorUserId) {
        return service().submit(tenantId, submissionId, actorUserId);
    }

    @Override
    public WorkflowTypeSubmission approve(String tenantId, String submissionId, String reviewerUserId) {
        return service().approve(tenantId, submissionId, reviewerUserId);
    }

    @Override
    public WorkflowTypeSubmission reject(String tenantId, String submissionId,
                                          String reviewerUserId, String reason) {
        return service().reject(tenantId, submissionId, reviewerUserId, reason);
    }

    @Override
    public WorkflowTypeSubmission revise(String tenantId, String submissionId,
                                          String actorUserId, DraftConfigs updatedDraftConfigs) {
        return service().revise(tenantId, submissionId, actorUserId, updatedDraftConfigs);
    }

    @Override
    public WorkflowTypeSubmission getById(String tenantId, String submissionId) {
        return service().getById(tenantId, submissionId);
    }

    @Override
    public List<WorkflowTypeSubmission> getPendingForTenant(String tenantId) {
        return service().getPendingForTenant(tenantId);
    }

    @Override
    public List<WorkflowTypeSubmission> getDraftsForUser(String tenantId, String actorUserId) {
        return service().getDraftsForUser(tenantId, actorUserId);
    }

    @Override
    public List<WorkflowTypeSubmission> getRejectedForUser(String tenantId, String actorUserId) {
        return service().getRejectedForUser(tenantId, actorUserId);
    }

    @Override
    public List<WorkflowTypeSubmission> getAllDraftsForTenant(String tenantId) {
        return service().getAllDraftsForTenant(tenantId);
    }
}
