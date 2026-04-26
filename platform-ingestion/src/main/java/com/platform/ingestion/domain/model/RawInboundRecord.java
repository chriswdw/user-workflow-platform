package com.platform.ingestion.domain.model;

import com.platform.domain.model.SourceType;

import java.util.Map;

/**
 * Normalised inbound record as delivered to the domain service.
 * All source-type-specific parsing (CSV, JDBC row, Kafka message) has already
 * been handled by the adapter — the domain service only sees flat string fields.
 */
public record RawInboundRecord(
        String tenantId,
        String workflowType,
        SourceType sourceType,
        String sourceRef,
        Map<String, String> rawFields,
        String makerUserId
) {}
