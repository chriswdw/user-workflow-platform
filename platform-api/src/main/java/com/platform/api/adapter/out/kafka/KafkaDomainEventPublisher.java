package com.platform.api.adapter.out.kafka;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.platform.domain.model.DomainEvent;
import com.platform.domain.ports.out.IDomainEventPublisher;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;

public class KafkaDomainEventPublisher implements IDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaDomainEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper,
                                     String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, event.workItemId(), payload);
            if (event.correlationId() != null) {
                producerRecord.headers().add(new RecordHeader(
                        "X-Correlation-ID",
                        event.correlationId().getBytes(StandardCharsets.UTF_8)));
            }
            producerRecord.headers().add(new RecordHeader(
                    "eventType",
                    event.eventType().getBytes(StandardCharsets.UTF_8)));
            kafkaTemplate.send(producerRecord);
        } catch (JacksonException e) {
            log.error("correlationId={} Failed to serialise domain event type={}", event.correlationId(), event.eventType(), e);
        }
    }
}
