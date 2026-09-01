package com.platform.api.adapter.out.kafka;

import com.platform.domain.model.DomainEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaDomainEventPublisherTest {

    private static final String TOPIC = "work-item-events";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);

    private final KafkaDomainEventPublisher publisher =
            new KafkaDomainEventPublisher(kafkaTemplate, objectMapper, TOPIC);

    @Test
    @SuppressWarnings("unchecked")
    void publish_withCorrelationId_sendsRecordWithBothHeaders() {
        DomainEvent event = new DomainEvent("evt-1", "tenant-1", "wi-1", "corr-1",
                "WORK_ITEM_CREATED", Instant.now(), Map.of());
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"eventId\":\"evt-1\"}");

        publisher.publish(event);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> producerRecord = captor.getValue();

        assertThat(producerRecord.topic()).isEqualTo(TOPIC);
        assertThat(producerRecord.key()).isEqualTo("wi-1");
        assertThat(producerRecord.value()).isEqualTo("{\"eventId\":\"evt-1\"}");
        assertThat(headerValue(producerRecord, "X-Correlation-ID")).isEqualTo("corr-1");
        assertThat(headerValue(producerRecord, "eventType")).isEqualTo("WORK_ITEM_CREATED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_withoutCorrelationId_omitsCorrelationHeader() {
        DomainEvent event = new DomainEvent("evt-2", "tenant-1", "wi-2", null,
                "WORK_ITEM_UPDATED", Instant.now(), Map.of());
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"eventId\":\"evt-2\"}");

        publisher.publish(event);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> producerRecord = captor.getValue();

        assertThat(producerRecord.headers().lastHeader("X-Correlation-ID")).isNull();
        assertThat(headerValue(producerRecord, "eventType")).isEqualTo("WORK_ITEM_UPDATED");
    }

    @Test
    void publish_serialisationFailure_logsAndDoesNotSend() {
        DomainEvent event = new DomainEvent("evt-3", "tenant-1", "wi-3", "corr-3",
                "WORK_ITEM_FAILED", Instant.now(), Map.of());
        when(objectMapper.writeValueAsString(event)).thenThrow(mock(JacksonException.class));

        publisher.publish(event);

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    private static String headerValue(ProducerRecord<String, String> producerRecord, String key) {
        Header header = producerRecord.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
