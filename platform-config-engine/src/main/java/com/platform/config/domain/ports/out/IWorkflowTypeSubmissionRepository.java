package com.platform.config.domain.ports.out;

import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;

import java.util.List;
import java.util.Optional;

public interface IWorkflowTypeSubmissionRepository {
    WorkflowTypeSubmission save(WorkflowTypeSubmission submission);
    Optional<WorkflowTypeSubmission> findById(String tenantId, String submissionId);
    List<WorkflowTypeSubmission> findByTenantAndStatus(String tenantId, SubmissionStatus status);
    List<WorkflowTypeSubmission> findByTenantAndStatusAndUser(String tenantId, SubmissionStatus status, String userId);
    boolean existsByTenantAndWorkflowType(String tenantId, String workflowType);
}
