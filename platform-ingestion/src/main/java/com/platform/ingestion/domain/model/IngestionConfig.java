package com.platform.ingestion.domain.model;

import com.platform.domain.model.SourceType;

import java.util.List;
import java.util.Objects;

public record IngestionConfig(
        String tenantId,
        String workflowType,
        SourceType sourceType,
        List<FieldMapping> fieldMappings,
        UnknownColumnPolicy unknownColumnPolicy,
        IdempotencyKeyStrategy idempotencyKeyStrategy,
        List<String> idempotencyKeyFields,
        String idempotencyExplicitField,
        String initialState
) {
    public IngestionConfig {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workflowType, "workflowType must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(idempotencyKeyStrategy, "idempotencyKeyStrategy must not be null");
        Objects.requireNonNull(initialState, "initialState must not be null");

        if (idempotencyKeyStrategy == IdempotencyKeyStrategy.EXPLICIT_FIELD) {
            if (idempotencyExplicitField == null || idempotencyExplicitField.isBlank()) {
                throw new IllegalArgumentException(
                        "IngestionConfig for '%s/%s': idempotencyExplicitField must be set when strategy is EXPLICIT_FIELD"
                                .formatted(tenantId, workflowType));
            }
        } else if (idempotencyKeyStrategy == IdempotencyKeyStrategy.COMPOSITE_HASH) {
            if (idempotencyKeyFields == null || idempotencyKeyFields.isEmpty()) {
                throw new IllegalArgumentException(
                        "IngestionConfig for '%s/%s': idempotencyKeyFields must be non-empty when strategy is COMPOSITE_HASH"
                                .formatted(tenantId, workflowType));
            }
        }
    }
}
