package com.platform.domain.model;

import java.time.Instant;
import java.util.Map;

public record DomainEvent(
    String eventId,
    String tenantId,
    String workItemId,
    String correlationId,
    String eventType,
    Instant timestamp,
    Map<String, Object> payload) {}
