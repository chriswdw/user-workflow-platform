package com.platform.config.domain.model;

public record ConfigValidationViolation(
        String message,
        ConfigType configType,
        String tenantId,
        String workflowType
) {}
