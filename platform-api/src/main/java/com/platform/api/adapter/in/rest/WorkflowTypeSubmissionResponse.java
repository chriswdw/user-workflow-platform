package com.platform.api.adapter.in.rest;

import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.WorkflowTypeSubmission;

import java.time.OffsetDateTime;

public record WorkflowTypeSubmissionResponse(
        String id,
        String tenantId,
        String workflowType,
        String displayName,
        String description,
        String statusCode,
        String statusDisplayName,
        DraftConfigs draftConfigs,
        String submittedBy,
        OffsetDateTime submittedAt,
        String reviewedBy,
        OffsetDateTime reviewedAt,
        String rejectionReason,
        int currentStep,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static WorkflowTypeSubmissionResponse from(WorkflowTypeSubmission s) {
        return new WorkflowTypeSubmissionResponse(
                s.id(), s.tenantId(), s.workflowType(), s.displayName(), s.description(),
                s.status().name(), s.statusDisplayName(),
                s.draftConfigs(),
                s.submittedBy(), s.submittedAt(),
                s.reviewedBy(), s.reviewedAt(), s.rejectionReason(),
                s.currentStep(), s.version(), s.createdAt(), s.updatedAt());
    }
}
