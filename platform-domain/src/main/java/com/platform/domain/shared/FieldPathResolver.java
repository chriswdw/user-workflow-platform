package com.platform.domain.shared;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves dot-notation field paths against a nested Map representing WorkItem.fields.
 * Used by the routing rule evaluator, blotter renderer, and audit log to access
 * arbitrary-depth fields without knowing the structure at compile time.
 *
 * Example: resolve(fields, "trade.notionalAmount.amount") navigates
 * fields["trade"]["notionalAmount"]["amount"].
 */
public final class FieldPathResolver {

    private FieldPathResolver() {}

    /**
     * Resolves a dot-notation path into a nested map.
     *
     * @param fields  the root fields map (WorkItem.fields)
     * @param dotPath dot-separated path, e.g. "counterparty.region" or "trade.notionalAmount.amount"
     * @return the value at that path, or empty if any segment is missing
     */
    @SuppressWarnings("unchecked")
    public static Optional<Object> resolve(Map<String, Object> fields, String dotPath) {
        String[] parts = dotPath.split("\\.", 2);
        Object value = fields.get(parts[0]);
        if (value == null) {
            return Optional.empty();
        }
        if (parts.length == 1) {
            return Optional.of(value);
        }
        if (value instanceof Map<?, ?> nested) {
            return resolve((Map<String, Object>) nested, parts[1]);
        }
        return Optional.empty();
    }
}
