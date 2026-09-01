package com.platform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * DomainEvent is the outbound Kafka event payload (see IDomainEventPublisher /
 * KafkaDomainEventPublisher). Plain data carrier, no compact-constructor validation, but every
 * consumer relies on its accessors carrying tenant/correlation context through — untested until
 * now.
 */
class DomainEventTest {

  @Test
  void carriesTenantAndCorrelationContextThroughToPayload() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    var event =
        new DomainEvent(
            "evt-1",
            "tenant-1",
            "wi-1",
            "corr-1",
            "WORK_ITEM_STATE_CHANGED",
            now,
            Map.of("previousState", "OPEN", "newState", "RESOLVED"));

    assertThat(event.tenantId()).isEqualTo("tenant-1");
    assertThat(event.workItemId()).isEqualTo("wi-1");
    assertThat(event.correlationId()).isEqualTo("corr-1");
    assertThat(event.eventType()).isEqualTo("WORK_ITEM_STATE_CHANGED");
    assertThat(event.payload()).containsEntry("previousState", "OPEN");
  }
}
