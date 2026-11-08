package com.platform.routing.domain.model;

import java.util.Objects;

/**
 * A single routing rule within a RoutingConfig. Rules are evaluated in ascending priority order;
 * first match wins. Inactive rules, and rules outside their {@link EffectivePeriod}, are skipped.
 */
public record RoutingRule(
    String id,
    String name,
    int priority,
    ConditionNode conditions,
    String targetGroupId,
    boolean active,
    EffectivePeriod effective) {

  public RoutingRule {
    Objects.requireNonNull(
        effective, "effective must not be null — use EffectivePeriod.unbounded()");
  }

  /** A rule with no start date and no expiry. */
  public RoutingRule(
      String id,
      String name,
      int priority,
      ConditionNode conditions,
      String targetGroupId,
      boolean active) {
    this(id, name, priority, conditions, targetGroupId, active, EffectivePeriod.unbounded());
  }
}
