package com.platform.api.adapter.in.rest;

import com.platform.config.domain.model.DraftConfigs;
import jakarta.validation.constraints.NotBlank;

record CreateSubmissionRequest(
        @NotBlank String workflowType,
        String displayName,
        String description,
        DraftConfigs draftConfigs
) {}
