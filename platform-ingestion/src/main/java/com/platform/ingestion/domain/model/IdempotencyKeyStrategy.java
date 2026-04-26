package com.platform.ingestion.domain.model;

public enum IdempotencyKeyStrategy {
    EXPLICIT_FIELD,
    COMPOSITE_HASH
}
