package com.platform.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * The central domain entity.
 * Pure instance data — no type-level metadata, no display properties.
 * All fields (status, assignedGroup, etc.) are immutable; "with" methods return new instances.
 */
public record WorkItem(
        String id,
        String tenantId,
        String workflowType,
        String correlationId,
        String configVersionId,
        String status,
        String assignedGroup,
        boolean routedByDefault,
        Map<String, Object> fields,
        Integer priorityScore,
        String priorityLevel,
        Instant priorityLastCalculatedAt,
        String pendingCheckerId,
        String pendingCheckerTransition,
        int version,
        String makerUserId,
        Instant createdAt,
        Instant updatedAt
) {

    public WorkItem withStatus(String newStatus) {
        return new WorkItem(id, tenantId, workflowType, correlationId, configVersionId,
                newStatus, assignedGroup, routedByDefault, fields,
                priorityScore, priorityLevel, priorityLastCalculatedAt,
                pendingCheckerId, pendingCheckerTransition, version, makerUserId, createdAt, Instant.now());
    }

    public WorkItem withAssignedGroup(String newGroup) {
        return new WorkItem(id, tenantId, workflowType, correlationId, configVersionId,
                status, newGroup, routedByDefault, fields,
                priorityScore, priorityLevel, priorityLastCalculatedAt,
                pendingCheckerId, pendingCheckerTransition, version, makerUserId, createdAt, Instant.now());
    }

    public WorkItem withMakerUserId(String userId) {
        return new WorkItem(id, tenantId, workflowType, correlationId, configVersionId,
                status, assignedGroup, routedByDefault, fields,
                priorityScore, priorityLevel, priorityLastCalculatedAt,
                pendingCheckerId, pendingCheckerTransition, version, userId, createdAt, Instant.now());
    }

    public WorkItem withFields(Map<String, Object> newFields) {
        return new WorkItem(id, tenantId, workflowType, correlationId, configVersionId,
                status, assignedGroup, routedByDefault, newFields,
                priorityScore, priorityLevel, priorityLastCalculatedAt,
                pendingCheckerId, pendingCheckerTransition, version, makerUserId, createdAt, Instant.now());
    }

    public WorkItem withPendingMakerChecker(String checkerId, String transitionName) {
        return new WorkItem(id, tenantId, workflowType, correlationId, configVersionId,
                status, assignedGroup, routedByDefault, fields,
                priorityScore, priorityLevel, priorityLastCalculatedAt,
                checkerId, transitionName, version, makerUserId, createdAt, Instant.now());
    }

    public WorkItem clearPendingMakerChecker() {
        return new WorkItem(id, tenantId, workflowType, correlationId, configVersionId,
                status, assignedGroup, routedByDefault, fields,
                priorityScore, priorityLevel, priorityLastCalculatedAt,
                null, null, version, makerUserId, createdAt, Instant.now());
    }
}
