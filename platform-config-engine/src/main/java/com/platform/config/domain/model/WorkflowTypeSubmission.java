package com.platform.config.domain.model;

import java.time.OffsetDateTime;

public record WorkflowTypeSubmission(
        String id,
        String tenantId,
        String workflowType,
        String displayName,
        String description,
        SubmissionStatus status,
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
) {}
