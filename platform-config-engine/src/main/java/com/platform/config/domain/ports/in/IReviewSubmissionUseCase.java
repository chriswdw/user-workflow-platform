package com.platform.config.domain.ports.in;

import com.platform.config.domain.model.WorkflowTypeSubmission;

public interface IReviewSubmissionUseCase {
    WorkflowTypeSubmission approve(String tenantId, String submissionId, String reviewerUserId);
    WorkflowTypeSubmission reject(String tenantId, String submissionId, String reviewerUserId, String reason);
}
