package com.platform.workflow.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public record WorkflowConfig(
    String id,
    String tenantId,
    String workflowType,
    String initialState,
    List<WorkflowState> states,
    List<WorkflowTransition> transitions,
    boolean active) {
  public WorkflowConfig {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(workflowType, "workflowType must not be null");
    Objects.requireNonNull(initialState, "initialState must not be null");
    Objects.requireNonNull(states, "states must not be null");
    Objects.requireNonNull(transitions, "transitions must not be null");

    var stateNames = states.stream().map(WorkflowState::name).collect(Collectors.toSet());

    if (!stateNames.contains(initialState)) {
      throw new IllegalArgumentException(
          "WorkflowConfig for '%s/%s': initialState '%s' is not defined in states %s"
              .formatted(tenantId, workflowType, initialState, stateNames));
    }
    for (var t : transitions) {
      if (!stateNames.contains(t.fromState())) {
        throw new IllegalArgumentException(
            "WorkflowConfig for '%s/%s': transition '%s' references unknown fromState '%s'"
                .formatted(tenantId, workflowType, t.name(), t.fromState()));
      }
      if (!stateNames.contains(t.toState())) {
        throw new IllegalArgumentException(
            "WorkflowConfig for '%s/%s': transition '%s' references unknown toState '%s'"
                .formatted(tenantId, workflowType, t.name(), t.toState()));
      }
    }
  }

  public Optional<WorkflowTransition> findTransition(String name) {
    return transitions.stream().filter(t -> t.name().equals(name)).findFirst();
  }

  public Optional<WorkflowState> findState(String name) {
    return states.stream().filter(s -> s.name().equals(name)).findFirst();
  }
}
