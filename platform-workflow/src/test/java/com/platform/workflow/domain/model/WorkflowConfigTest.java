package com.platform.workflow.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowConfigTest {

    private static final WorkflowConfig CONFIG = new WorkflowConfig(
            "cfg-1", "tenant-1", "TEST_TYPE", "OPEN",
            List.of(
                    new WorkflowState("OPEN",   false, List.of("ANALYST")),
                    new WorkflowState("CLOSED", true,  List.of("ANALYST"))
            ),
            List.of(
                    new WorkflowTransition("close", "OPEN", "CLOSED",
                            TransitionTrigger.USER_ACTION, null,
                            List.of("ANALYST"), false, List.of(), List.of())
            ),
            true
    );

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
}
