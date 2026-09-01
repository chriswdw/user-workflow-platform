package com.platform.config.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * isComplete() gates whether a draft submission can move to submit-for-approval — it is pure logic
 * with a real business meaning (a submission needs both a blotter and detail view config before
 * it's reviewable), so every short-circuit branch is covered directly.
 */
class DraftConfigsTest {

  private static final Map<String, Object> NON_EMPTY = Map.of("k", "v");

  @Test
  void isComplete_falseWhenBlotterConfigIsNull() {
    DraftConfigs drafts = new DraftConfigs(null, null, null, null, null, NON_EMPTY);
    assertThat(drafts.isComplete()).isFalse();
  }

  @Test
  void isComplete_falseWhenBlotterConfigIsEmpty() {
    DraftConfigs drafts = new DraftConfigs(null, null, null, null, Map.of(), NON_EMPTY);
    assertThat(drafts.isComplete()).isFalse();
  }

  @Test
  void isComplete_falseWhenDetailViewConfigIsNull() {
    DraftConfigs drafts = new DraftConfigs(null, null, null, null, NON_EMPTY, null);
    assertThat(drafts.isComplete()).isFalse();
  }

  @Test
  void isComplete_falseWhenDetailViewConfigIsEmpty() {
    DraftConfigs drafts = new DraftConfigs(null, null, null, null, NON_EMPTY, Map.of());
    assertThat(drafts.isComplete()).isFalse();
  }

  @Test
  void isComplete_trueWhenBothBlotterAndDetailViewConfigsAreNonEmpty() {
    DraftConfigs drafts = new DraftConfigs(null, null, null, null, NON_EMPTY, NON_EMPTY);
    assertThat(drafts.isComplete()).isTrue();
  }
}
