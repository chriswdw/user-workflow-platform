package com.platform.workflow.domain.model;

import java.util.List;

public record WorkflowState(
        String name,
        boolean terminal,
        List<String> allowedRoles
) {}
