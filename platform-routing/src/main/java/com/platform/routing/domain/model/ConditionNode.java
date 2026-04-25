package com.platform.routing.domain.model;

/**
 * Recursive condition tree node used in routing rules and priority scoring.
 * A condition is either a LEAF (tests a single field) or a GROUP (combines
 * children with AND/OR logic), supporting arbitrary nesting.
 *
 * Example: (region=EMEA AND amount>=1M) OR flagged=REGULATORY_HOLD
 */
public sealed interface ConditionNode permits LeafCondition, GroupCondition {}
