package com.platform.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable audit log entry. Written once by domain services, never updated.
 * changedFields values are UNMASKED — the audit log is the authoritative unmasked record.
 * Access is controlled by audit query roles.
 */
public record AuditEntry(
        String id,
        String tenantId,
        String workItemId,
        String correlationId,
        AuditEventType eventType,
        String previousState,
        String newState,
        String transitionName,
        List<ChangedField> changedFields,
        String actorUserId,
        String actorRole,
        Instant timestamp,
        String idempotencyKey
) {
    public record ChangedField(String fieldPath, Object previousValue, Object newValue) {}
}
