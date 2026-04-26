package com.platform.audit.domain.model;

import com.platform.domain.model.AuditEventType;

import java.time.Instant;
import java.util.List;

public record AuditQuery(
        String tenantId,
        String workItemId,
        List<AuditEventType> eventTypes,
        Instant from,
        Instant to
) {}
