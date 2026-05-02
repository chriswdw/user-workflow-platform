package com.platform.config.domain.ports.in;

import com.platform.config.domain.model.DraftConfigs;

public record CreateSubmissionCommand(
        String tenantId,
        String actorUserId,
        String workflowType,
        String displayName,
        String description,
        DraftConfigs draftConfigs
) {}
