package com.platform.routing.domain.service;

import com.platform.domain.shared.FieldPathResolver;
import com.platform.routing.domain.model.ConditionNode;
import com.platform.routing.domain.model.GroupCondition;
import com.platform.routing.domain.model.LeafCondition;
import com.platform.routing.domain.model.Operator;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Evaluates a ConditionNode tree against a WorkItem.fields map.
 * Package-private — consumed only by RoutingService and PriorityScoringEngine.
 *
 * LEAF nodes: resolve the dot-notation field path via FieldPathResolver, then
 * apply the operator. GT/GTE/LT/LTE use BigDecimal comparison to support
 * MONETARY and DECIMAL fields with full precision.
 *
 * GROUP nodes: combine children with AND (all must match) or OR (any must match).
 */
final class ConditionEvaluator {

    private ConditionEvaluator() {}

    static boolean evaluate(ConditionNode node, Map<String, Object> fields) {
        return switch (node) {
            case LeafCondition leaf -> evaluateLeaf(leaf, fields);
            case GroupCondition group -> evaluateGroup(group, fields);
        };
    }

    private static boolean evaluateLeaf(LeafCondition leaf, Map<String, Object> fields) {
        if (leaf.operator() == Operator.EXISTS) {
            return FieldPathResolver.resolve(fields, leaf.field()).isPresent();
        }

        Optional<Object> resolved = FieldPathResolver.resolve(fields, leaf.field());
        if (resolved.isEmpty()) {
            return false;
        }

        Object fieldValue = resolved.get();
        Object comparand = leaf.value();

        return switch (leaf.operator()) {
            case EQ       -> fieldValue.toString().equals(comparand.toString());
            case NEQ      -> !fieldValue.toString().equals(comparand.toString());
            case GT       -> compareBigDecimal(fieldValue, comparand) > 0;
            case GTE      -> compareBigDecimal(fieldValue, comparand) >= 0;
            case LT       -> compareBigDecimal(fieldValue, comparand) < 0;
            case LTE      -> compareBigDecimal(fieldValue, comparand) <= 0;
            case IN       -> toStringList(comparand).contains(fieldValue.toString());
            case NOT_IN   -> !toStringList(comparand).contains(fieldValue.toString());
            case CONTAINS -> fieldValue.toString().contains(comparand.toString());
            case REGEX    -> Pattern.matches(comparand.toString(), fieldValue.toString());
            case EXISTS   -> true; // unreachable — handled above
        };
    }

    private static boolean evaluateGroup(GroupCondition group, Map<String, Object> fields) {
        return switch (group.logicalOperator()) {
            case AND -> group.children().stream().allMatch(child -> evaluate(child, fields));
            case OR  -> group.children().stream().anyMatch(child -> evaluate(child, fields));
        };
    }

    private static int compareBigDecimal(Object fieldValue, Object comparand) {
        try {
            BigDecimal a = new BigDecimal(fieldValue.toString().trim());
            BigDecimal b = new BigDecimal(comparand.toString().trim());
            return a.compareTo(b);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Numeric operator applied to non-numeric values: '"
                    + fieldValue + "' vs '" + comparand + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value instanceof Collection<?> c) {
            return c.stream().map(Object::toString).toList();
        }
        return List.of(value.toString());
    }
}
