package com.platform.routing.domain.model;

/**
 * Tests a single WorkItem field against a value using the specified operator.
 * field is a dot-notation path resolved by FieldPathResolver (e.g. "counterparty.region").
 * For MONETARY fields, BigDecimal comparison is applied when operator is GT/GTE/LT/LTE.
 */
public record LeafCondition(
        String field,
        Operator operator,
        Object value
) implements ConditionNode {}
