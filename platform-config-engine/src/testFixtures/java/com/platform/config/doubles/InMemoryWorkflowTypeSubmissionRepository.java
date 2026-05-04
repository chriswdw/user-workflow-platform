package com.platform.config.doubles;

import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.out.IWorkflowTypeSubmissionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryWorkflowTypeSubmissionRepository implements IWorkflowTypeSubmissionRepository {

    private final List<WorkflowTypeSubmission> store = new ArrayList<>();

    @Override
    public WorkflowTypeSubmission save(WorkflowTypeSubmission submission) {
        store.removeIf(s -> s.id().equals(submission.id()));
        store.add(submission);
        return submission;
    }

    @Override
    public Optional<WorkflowTypeSubmission> findById(String tenantId, String submissionId) {
        return store.stream()
                .filter(s -> s.tenantId().equals(tenantId) && s.id().equals(submissionId))
                .findFirst();
    }

    @Override
    public List<WorkflowTypeSubmission> findByTenantAndStatus(String tenantId, SubmissionStatus status) {
        return store.stream()
                .filter(s -> s.tenantId().equals(tenantId) && s.status() == status)
                .toList();
    }

    @Override
    public List<WorkflowTypeSubmission> findByTenantAndStatusAndUser(String tenantId,
                                                                      SubmissionStatus status,
                                                                      String userId) {
        return store.stream()
                .filter(s -> s.tenantId().equals(tenantId)
                        && s.status() == status
                        && s.submittedBy().equals(userId))
                .toList();
    }

    @Override
    public boolean existsByTenantAndWorkflowType(String tenantId, String workflowType) {
        return store.stream()
                .anyMatch(s -> s.tenantId().equals(tenantId)
                        && s.workflowType().equals(workflowType)
                        && s.status() != SubmissionStatus.REJECTED);
    }

    public void reset() {
        store.clear();
    }
}
