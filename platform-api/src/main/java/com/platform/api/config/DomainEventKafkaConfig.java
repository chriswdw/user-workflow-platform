package com.platform.api.config;

import tools.jackson.databind.ObjectMapper;
import com.platform.api.adapter.out.kafka.KafkaDomainEventPublisher;
import com.platform.domain.ports.out.IDomainEventPublisher;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class DomainEventKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${platform.events.topic:platform.domain-events}")
    private String domainEventsTopic;

    @Bean
    KafkaTemplate<String, String> domainEventKafkaTemplate() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1,
                ProducerConfig.RETRIES_CONFIG, 3,
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true
        );
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    IDomainEventPublisher domainEventPublisher(KafkaTemplate<String, String> domainEventKafkaTemplate,
                                               ObjectMapper objectMapper) {
        return new KafkaDomainEventPublisher(domainEventKafkaTemplate, objectMapper, domainEventsTopic);
    }
}
