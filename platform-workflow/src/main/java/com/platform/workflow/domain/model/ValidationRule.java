package com.platform.workflow.domain.model;

/**
 * A precondition that must pass before a transition is allowed.
 * operator is one of: EXISTS, EQ, NEQ — matches the routing Operator vocabulary for consistency.
 * value is null when operator is EXISTS.
 */
public record ValidationRule(
        String field,
        String operator,
        String value
) {}
