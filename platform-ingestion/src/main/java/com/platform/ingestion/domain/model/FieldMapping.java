package com.platform.ingestion.domain.model;

/**
 * Maps a source field name (column header, JSON path, DB column) to a dot-notation
 * path in WorkItem.fields. If required is true and the source field is absent,
 * the record is rejected.
 */
public record FieldMapping(
        String sourceField,
        String targetField,
        boolean required
) {}
