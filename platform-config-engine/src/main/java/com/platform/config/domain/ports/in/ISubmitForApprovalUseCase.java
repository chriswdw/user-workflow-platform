package com.platform.config.domain.ports.in;

import com.platform.config.domain.model.WorkflowTypeSubmission;

public interface ISubmitForApprovalUseCase {
    WorkflowTypeSubmission submit(String tenantId, String submissionId, String actorUserId);
}
