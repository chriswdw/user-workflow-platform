package com.platform.workflow.domain.model;

import java.util.List;

public record WorkflowTransition(
        String name,
        String fromState,
        String toState,
        TransitionTrigger trigger,
        String systemEventType,
        List<String> allowedRoles,
        boolean requiresMakerChecker,
        List<TransitionAction> actions,
        List<ValidationRule> validationRules
) {}
