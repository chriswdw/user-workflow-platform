package com.platform.config.domain.ports.in;

import com.platform.config.domain.model.WorkflowTypeSubmission;

import java.util.List;

public interface IGetSubmissionUseCase {
    WorkflowTypeSubmission getById(String tenantId, String submissionId);
    List<WorkflowTypeSubmission> getPendingForTenant(String tenantId);
    List<WorkflowTypeSubmission> getDraftsForUser(String tenantId, String actorUserId);
    List<WorkflowTypeSubmission> getRejectedForUser(String tenantId, String actorUserId);
}
