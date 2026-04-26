package com.platform.ingestion.domain.model;

import com.platform.domain.model.SourceType;

import java.util.List;

/**
 * Config driving the ingestion adapter for a specific tenant, workflowType, and sourceType.
 * Loaded from platform-config-engine at startup and hot-reloaded on change.
 */
public record IngestionConfig(
        String tenantId,
        String workflowType,
        SourceType sourceType,
        List<FieldMapping> fieldMappings,
        UnknownColumnPolicy unknownColumnPolicy,
        IdempotencyKeyStrategy idempotencyKeyStrategy,
        List<String> idempotencyKeyFields,   // ordered field names for COMPOSITE_HASH
        String idempotencyExplicitField,      // source field name for EXPLICIT_FIELD
        String initialState
) {}
