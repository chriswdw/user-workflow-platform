package com.platform.ingestion.domain.model;

import com.platform.domain.model.WorkItem;

/**
 * Outcome of an ingest operation. All expected business outcomes are modelled here;
 * only unexpected system errors (missing config, repository failure) throw exceptions.
 */
public sealed interface IngestionResult permits
        IngestionResult.Created,
        IngestionResult.Duplicate,
        IngestionResult.Rejected {

    /** A new WorkItem was created and saved. */
    record Created(WorkItem workItem) implements IngestionResult {}

    /** A record with the same idempotency key already exists — silently discarded. */
    record Duplicate(String idempotencyKey) implements IngestionResult {}

    /** The record violated a domain rule and was not ingested. */
    record Rejected(String reason) implements IngestionResult {}
}
