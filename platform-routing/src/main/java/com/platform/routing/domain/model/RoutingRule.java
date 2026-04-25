package com.platform.routing.domain.model;

/**
 * A single routing rule within a RoutingConfig.
 * Rules are evaluated in ascending priority order; first match wins.
 * Inactive rules are skipped.
 */
public record RoutingRule(
        String id,
        String name,
        int priority,
        ConditionNode conditions,
        String targetGroupId,
        boolean active
) {}
