package com.platform.domain.ports.out;

import com.platform.domain.model.DomainEvent;

/**
 * Output port for publishing domain events to downstream consumers.
 * Implemented by the Kafka adapter in production; no-op in tests without Kafka.
 */
public interface IDomainEventPublisher {
    void publish(DomainEvent event);
}
