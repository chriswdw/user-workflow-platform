package com.platform.workflow.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowConfigTest {

  private static final WorkflowConfig CONFIG =
      new WorkflowConfig(
          "cfg-1",
          "tenant-1",
          "TEST_TYPE",
          "OPEN",
          List.of(
              new WorkflowState("OPEN", false, List.of("ANALYST")),
              new WorkflowState("CLOSED", true, List.of("ANALYST"))),
          List.of(
              new WorkflowTransition(
                  "close",
                  "OPEN",
                  "CLOSED",
                  TransitionTrigger.USER_ACTION,
                  null,
                  List.of("ANALYST"),
                  false,
                  List.of(),
                  List.of())),
          true);

  @Test
  void findTransition_returnsTransitionWhenFound() {
    assertThat(CONFIG.findTransition("close"))
        .isPresent()
        .get()
        .extracting(WorkflowTransition::name)
        .isEqualTo("close");
  }

  @Test
  void findTransition_returnsEmptyWhenNotFound() {
    assertThat(CONFIG.findTransition("nonexistent")).isEmpty();
  }

  @Test
  void findState_returnsStateWhenFound() {
    assertThat(CONFIG.findState("OPEN"))
        .isPresent()
        .get()
        .extracting(WorkflowState::name)
        .isEqualTo("OPEN");
  }

  @Test
  void findState_returnsEmptyWhenNotFound() {
    assertThat(CONFIG.findState("NONEXISTENT")).isEmpty();
  }

  @Test
  void constructor_initialStateNotInStates_throws() {
    var states = List.of(new WorkflowState("OPEN", false, List.of("ANALYST")));
    var noTransitions = List.<WorkflowTransition>of();
    assertThatThrownBy(
            () ->
                new WorkflowConfig("id", "t1", "TYPE", "NONEXISTENT", states, noTransitions, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("initialState")
        .hasMessageContaining("NONEXISTENT");
  }

  @Test
  void constructor_transitionReferencesUnknownFromState_throws() {
    var states = List.of(new WorkflowState("OPEN", false, List.of()));
    var transitions =
        List.of(
            new WorkflowTransition(
                "close",
                "UNKNOWN",
                "OPEN",
                TransitionTrigger.USER_ACTION,
                null,
                List.of(),
                false,
                List.of(),
                List.of()));
    assertThatThrownBy(
            () -> new WorkflowConfig("id", "t1", "TYPE", "OPEN", states, transitions, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fromState")
        .hasMessageContaining("UNKNOWN");
  }

  @Test
  void constructor_transitionReferencesUnknownToState_throws() {
    var states = List.of(new WorkflowState("OPEN", false, List.of()));
    var transitions =
        List.of(
            new WorkflowTransition(
                "close",
                "OPEN",
                "UNKNOWN",
                TransitionTrigger.USER_ACTION,
                null,
                List.of(),
                false,
                List.of(),
                List.of()));
    assertThatThrownBy(
            () -> new WorkflowConfig("id", "t1", "TYPE", "OPEN", states, transitions, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("toState")
        .hasMessageContaining("UNKNOWN");
  }

  @Test
  void constructor_valid_succeeds() {
    var states =
        List.of(
            new WorkflowState("OPEN", false, List.of()),
            new WorkflowState("CLOSED", true, List.of()));
    var transitions =
        List.of(
            new WorkflowTransition(
                "close",
                "OPEN",
                "CLOSED",
                TransitionTrigger.USER_ACTION,
                null,
                List.of(),
                false,
                List.of(),
                List.of()));
    assertThatCode(() -> new WorkflowConfig("id", "t1", "TYPE", "OPEN", states, transitions, true))
        .doesNotThrowAnyException();
  }
}
