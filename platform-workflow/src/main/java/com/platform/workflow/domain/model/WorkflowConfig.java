package com.platform.workflow.domain.model;

import java.util.List;
import java.util.Optional;

public record WorkflowConfig(
        String id,
        String tenantId,
        String workflowType,
        String initialState,
        List<WorkflowState> states,
        List<WorkflowTransition> transitions,
        boolean active
) {

    public Optional<WorkflowTransition> findTransition(String name) {
        return transitions.stream()
                .filter(t -> t.name().equals(name))
                .findFirst();
    }

    public Optional<WorkflowState> findState(String name) {
        return states.stream()
                .filter(s -> s.name().equals(name))
                .findFirst();
    }
}
