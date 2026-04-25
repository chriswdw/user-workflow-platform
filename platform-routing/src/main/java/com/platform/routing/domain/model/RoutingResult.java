package com.platform.routing.domain.model;

/**
 * The outcome of routing a single work item.
 * assignedGroupId is always non-null — the routing engine guarantees group assignment.
 * matchedRuleId is null when routedByDefault is true.
 */
public record RoutingResult(
        String assignedGroupId,
        boolean routedByDefault,
        String matchedRuleId
) {}
