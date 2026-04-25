package com.platform.workflow.domain.model;

/**
 * Input to the workflow use case.
 * actorRole is the role the actor is operating under — checked against transition.allowedRoles.
 */
public record TransitionCommand(
        String workItemId,
        String tenantId,
        String transitionName,
        String actorUserId,
        String actorRole
) {}
