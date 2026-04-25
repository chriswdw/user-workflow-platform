package com.platform.routing.domain.model;

import java.util.Map;

/**
 * The routing domain's view of a work item — contains only what the routing
 * engine needs. Fields is a nested map matching the WorkItem.fields JSONB
 * structure; all field references use dot-notation paths via FieldPathResolver.
 */
public record WorkItemToRoute(
        String id,
        String tenantId,
        String workflowType,
        String makerUserId,
        Map<String, Object> fields
) {}
