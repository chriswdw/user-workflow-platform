package com.platform.api.doubles;

import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.CreateSubmissionCommand;
import com.platform.config.domain.ports.in.ICreateWorkflowTypeSubmissionUseCase;
import com.platform.config.domain.ports.in.IGetSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviewSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviseSubmissionUseCase;
import com.platform.config.domain.ports.in.ISaveDraftUseCase;
import com.platform.config.domain.ports.in.ISubmitForApprovalUseCase;
import com.platform.config.domain.ports.out.IConfigDocumentWriter;
import com.platform.config.domain.ports.out.IWorkflowTypeSubmissionRepository;
import com.platform.config.domain.service.WorkflowTypeSubmissionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemorySubmissionPort
        implements ICreateWorkflowTypeSubmissionUseCase,
                   ISaveDraftUseCase,
                   ISubmitForApprovalUseCase,
                   IReviewSubmissionUseCase,
                   IReviseSubmissionUseCase,
                   IGetSubmissionUseCase {

    private final List<WorkflowTypeSubmission> store = new ArrayList<>();

    private final IWorkflowTypeSubmissionRepository repo = new IWorkflowTypeSubmissionRepository() {
        @Override
        public WorkflowTypeSubmission save(WorkflowTypeSubmission s) {
            store.removeIf(e -> e.id().equals(s.id()));
            store.add(s);
            return s;
        }
        @Override
        public Optional<WorkflowTypeSubmission> findById(String tenantId, String id) {
            return store.stream()
                    .filter(s -> s.tenantId().equals(tenantId) && s.id().equals(id))
                    .findFirst();
        }
        @Override
        public List<WorkflowTypeSubmission> findByTenantAndStatus(String tenantId, SubmissionStatus status) {
            return store.stream()
                    .filter(s -> s.tenantId().equals(tenantId) && s.status() == status)
                    .toList();
        }
        @Override
        public List<WorkflowTypeSubmission> findByTenantAndStatusAndUser(String tenantId, SubmissionStatus status, String userId) {
            return store.stream()
                    .filter(s -> s.tenantId().equals(tenantId) && s.status() == status && s.submittedBy().equals(userId))
                    .toList();
        }
        @Override
        public boolean existsByTenantAndWorkflowType(String tenantId, String workflowType) {
            return store.stream().anyMatch(s ->
                    s.tenantId().equals(tenantId)
                    && s.workflowType().equals(workflowType)
                    && s.status() != SubmissionStatus.REJECTED);
        }
    };

    private final IConfigDocumentWriter noopWriter = new IConfigDocumentWriter() {
        @Override
        public void saveAll(List<ConfigDocument> documents) { /* no-op */ }
    };

    private WorkflowTypeSubmissionService service() {
        return new WorkflowTypeSubmissionService(repo, noopWriter, true);
    }

    public void seed(WorkflowTypeSubmission submission) {
        store.removeIf(s -> s.id().equals(submission.id()));
        store.add(submission);
    }

    public void reset() {
        store.clear();
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
    public WorkflowTypeSubmission reject(String tenantId, String submissionId, String reviewerUserId, String reason) {
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
