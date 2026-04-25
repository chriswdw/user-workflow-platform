package com.platform.routing.domain.model;

import java.util.List;

/**
 * Combines child condition nodes with AND or OR logic.
 * Children can be LeafCondition or GroupCondition, enabling arbitrary nesting.
 */
public record GroupCondition(
        LogicalOperator logicalOperator,
        List<ConditionNode> children
) implements ConditionNode {}
