package com.platform.routing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EffectivePeriodTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

  @Test
  void unboundedPeriodIsAlwaysEffective() {
    EffectivePeriod period = EffectivePeriod.unbounded();

    assertThat(period.isEffectiveOn(LocalDate.MIN)).isTrue();
    assertThat(period.isEffectiveOn(DAY)).isTrue();
    assertThat(period.isEffectiveOn(LocalDate.MAX)).isTrue();
  }

  @Test
  void fromBoundIsInclusive() {
    EffectivePeriod period = new EffectivePeriod(DAY, null);

    assertThat(period.isEffectiveOn(DAY.minusDays(1))).isFalse();
    assertThat(period.isEffectiveOn(DAY)).isTrue();
    assertThat(period.isEffectiveOn(DAY.plusDays(400))).isTrue();
  }

  @Test
  void toBoundIsInclusive() {
    EffectivePeriod period = new EffectivePeriod(null, DAY);

    assertThat(period.isEffectiveOn(DAY.minusDays(400))).isTrue();
    assertThat(period.isEffectiveOn(DAY)).isTrue();
    assertThat(period.isEffectiveOn(DAY.plusDays(1))).isFalse();
  }

  @Test
  void singleDayWindowIsEffectiveOnlyOnThatDay() {
    EffectivePeriod period = new EffectivePeriod(DAY, DAY);

    assertThat(period.isEffectiveOn(DAY.minusDays(1))).isFalse();
    assertThat(period.isEffectiveOn(DAY)).isTrue();
    assertThat(period.isEffectiveOn(DAY.plusDays(1))).isFalse();
  }

  @Test
  void endBeforeStartIsRejectedAtConstruction() {
    assertThatThrownBy(() -> new EffectivePeriod(DAY, DAY.minusDays(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be before");
  }
}
