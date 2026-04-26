package com.platform.workflow.domain.model;

import java.util.Map;

/**
 * Input to the workflow use case.
 * actorRole is the role the actor is operating under — checked against transition.allowedRoles.
 * additionalFields are user-supplied field values collected by the UI action form; merged into
 * WorkItem.fields before the state transition fires.
 */
public record TransitionCommand(
        String workItemId,
        String tenantId,
        String transitionName,
        String actorUserId,
        String actorRole,
        Map<String, Object> additionalFields
) {
    public TransitionCommand {
        additionalFields = additionalFields == null ? Map.of() : Map.copyOf(additionalFields);
    }
}
