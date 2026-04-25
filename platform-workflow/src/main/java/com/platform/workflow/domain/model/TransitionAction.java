package com.platform.workflow.domain.model;

import java.util.Map;

/**
 * An action executed as part of a transition.
 * config carries type-specific parameters (e.g. targetGroup for REASSIGN_GROUP).
 */
public record TransitionAction(
        TransitionActionType type,
        Map<String, String> config,
        OnFailure onFailure
) {}
