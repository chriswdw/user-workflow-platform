package com.platform.routing.domain.model;

import java.time.LocalDate;

/**
 * The calendar dates between which a routing rule is in force, matching the optional {@code
 * effectiveFrom} / {@code effectiveTo} ISO dates in the routing-config schema. Both bounds are
 * inclusive: a rule is effective on its first and on its last day. A missing bound means "no start"
 * or "no expiry"; the nulls are encapsulated here so callers only ever ask {@link
 * #isEffectiveOn(LocalDate)}.
 */
public record EffectivePeriod(LocalDate from, LocalDate to) {

  public EffectivePeriod {
    if (from != null && to != null && to.isBefore(from)) {
      throw new IllegalArgumentException(
          "effectiveTo (" + to + ") must not be before effectiveFrom (" + from + ")");
    }
  }

  /** A period with no start and no expiry: always in force. */
  public static EffectivePeriod unbounded() {
    return new EffectivePeriod(null, null);
  }

  public boolean isEffectiveOn(LocalDate date) {
    boolean started = from == null || !date.isBefore(from);
    boolean notExpired = to == null || !date.isAfter(to);
    return started && notExpired;
  }
}
