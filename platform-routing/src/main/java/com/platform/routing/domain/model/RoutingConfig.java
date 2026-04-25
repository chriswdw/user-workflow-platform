package com.platform.routing.domain.model;

import java.util.List;

/**
 * Full routing configuration for a (tenantId, workflowType) pair.
 * defaultGroupId is always present — the routing invariant is structurally
 * guaranteed: every work item will be assigned to a group.
 * Rules are stored in evaluation order (ascending priority).
 */
public record RoutingConfig(
        String id,
        String tenantId,
        String workflowType,
        String defaultGroupId,
        boolean alertOnDefault,
        List<RoutingRule> rules
) {}
