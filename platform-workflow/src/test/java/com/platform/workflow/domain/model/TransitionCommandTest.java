package com.platform.workflow.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransitionCommandTest {

    @Test
    void nullAdditionalFieldsDefaultsToAnEmptyMap() {
        TransitionCommand command = new TransitionCommand(
                "wi-1", "tenant-1", "escalate", "user-1", "ANALYST", null);

        assertThat(command.additionalFields()).isEmpty();
    }

    @Test
    void providedAdditionalFieldsAreCopiedAsIs() {
        TransitionCommand command = new TransitionCommand(
                "wi-1", "tenant-1", "escalate", "user-1", "ANALYST", Map.of("reason", "urgent"));

        assertThat(command.additionalFields()).containsEntry("reason", "urgent");
    }
}
