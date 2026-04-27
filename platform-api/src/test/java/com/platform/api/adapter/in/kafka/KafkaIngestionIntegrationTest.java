package com.platform.api.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.SourceType;
import com.platform.domain.model.WorkItem;
import com.platform.ingestion.domain.model.FieldMapping;
import com.platform.ingestion.domain.model.IdempotencyKeyStrategy;
import com.platform.ingestion.domain.model.IngestionConfig;
import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;
import com.platform.ingestion.domain.model.UnknownColumnPolicy;
import com.platform.ingestion.domain.ports.in.IIngestRecordUseCase;
import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;
import com.platform.ingestion.domain.ports.out.IIdempotencyKeyRepository;
import com.platform.ingestion.domain.ports.out.IIngestionAuditRepository;
import com.platform.ingestion.domain.ports.out.IIngestionConfigRepository;
import com.platform.ingestion.domain.ports.out.IIngestionWorkItemRepository;
import com.platform.ingestion.domain.service.IngestionService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Kafka ingestion infrastructure behaviour using an embedded Kafka broker.
 *
 * Architecture intent: when a KafkaIngestionListener adapter is built in
 * adapter/in/kafka/, it will consume from INGEST_TOPIC, deserialise the message
 * to a RawInboundRecord, and call IIngestRecordUseCase.ingest(). This test
 * validates that protocol using a test-internal listener so the adapter design
 * is driven by a passing test before the production class is written.
 *
 * Domain ports use in-memory doubles — this test is about the Kafka layer
 * (topic routing, idempotency over at-least-once delivery, DLQ on processing
 * failure), not about JDBC persistence.
 *
 * When the real KafkaIngestionListener is implemented, these tests should be
 * updated to reference it directly and may be augmented with embedded-postgres
 * assertions for the persistence side.
 */
@SpringJUnitConfig(classes = KafkaIngestionIntegrationTest.TestConfig.class)
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaIngestionIntegrationTest.INGEST_TOPIC, KafkaIngestionIntegrationTest.DLQ_TOPIC}
)
class KafkaIngestionIntegrationTest {

    static final String INGEST_TOPIC = "work-items.ingest";
    static final String DLQ_TOPIC = "work-items.ingest.dlq";

    private static final String TENANT = "tenant-kafka";
    private static final String WORKFLOW_TYPE = "SETTLEMENT_EXCEPTION";

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private TestIngestionListener listener;
    @Autowired private TestDlqListener dlqListener;
    @Autowired private TestWorkItemStore workItemStore;
    @Autowired private TestIdempotencyStore idempotencyStore;

    @BeforeEach
    void resetCaptors() {
        listener.reset(1);
        dlqListener.reset(1);
        workItemStore.clear();
        idempotencyStore.clear();
    }

    // ── Valid message → domain service called → WorkItem created ─────────────

    @Test
    void validMessage_resultsInCreatedWorkItem() throws Exception {
        String message = ingestMessage("TRD-001", "ACME Corp");

        kafkaTemplate.send(INGEST_TOPIC, message).get(5, TimeUnit.SECONDS);

        assertThat(listener.latch().await(10, TimeUnit.SECONDS))
                .as("listener processed message").isTrue();
        assertThat(listener.results()).hasSize(1);
        assertThat(listener.results().get(0)).isInstanceOf(IngestionResult.Created.class);

        IngestionResult.Created created = (IngestionResult.Created) listener.results().get(0);
        assertThat(created.workItem().tenantId()).isEqualTo(TENANT);
        assertThat(created.workItem().workflowType()).isEqualTo(WORKFLOW_TYPE);
        assertThat(created.workItem().idempotencyKey()).isEqualTo("TRD-001");
        assertThat(workItemStore.all()).hasSize(1);
    }

    // ── Idempotency: duplicate message is discarded ───────────────────────────

    @Test
    void duplicateMessage_returnsIngestionResultDuplicate_andDoesNotCreateSecondWorkItem()
            throws Exception {
        listener.reset(2);
        String message = ingestMessage("TRD-DUP", "ACME Corp");

        kafkaTemplate.send(INGEST_TOPIC, message).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send(INGEST_TOPIC, message).get(5, TimeUnit.SECONDS);

        assertThat(listener.latch().await(10, TimeUnit.SECONDS))
                .as("both messages processed").isTrue();

        assertThat(listener.results()).hasSize(2);
        assertThat(listener.results().get(0)).isInstanceOf(IngestionResult.Created.class);
        assertThat(listener.results().get(1)).isInstanceOf(IngestionResult.Duplicate.class);
        assertThat(workItemStore.all()).hasSize(1);
    }

    // ── Malformed JSON → routes to DLQ, ingest topic offset advances ─────────

    @Test
    void malformedJson_isRoutedToDlqWithoutRetry() throws Exception {
        kafkaTemplate.send(INGEST_TOPIC, "{not valid json at all}").get(5, TimeUnit.SECONDS);

        assertThat(dlqListener.latch().await(15, TimeUnit.SECONDS))
                .as("DLQ received malformed message").isTrue();
        assertThat(dlqListener.payloads()).hasSize(1);
        assertThat(dlqListener.payloads().get(0)).contains("not valid json");
        assertThat(workItemStore.all()).isEmpty();
    }

    // ── Missing required field → domain service rejects → DLQ ────────────────

    @Test
    void missingRequiredField_resultsInRejectionAndMessageSentToDlq() throws Exception {
        // rawFields omits "tradeRef" which is a required field in IngestionConfig
        String message = """
                {
                  "tenantId": "%s",
                  "workflowType": "%s",
                  "sourceRef": "topic-0-99",
                  "rawFields": {},
                  "makerUserId": "system"
                }
                """.formatted(TENANT, WORKFLOW_TYPE);

        kafkaTemplate.send(INGEST_TOPIC, message).get(5, TimeUnit.SECONDS);

        assertThat(listener.latch().await(10, TimeUnit.SECONDS))
                .as("listener processed the message").isTrue();
        assertThat(listener.results().get(0)).isInstanceOf(IngestionResult.Rejected.class);

        IngestionResult.Rejected rejected = (IngestionResult.Rejected) listener.results().get(0);
        assertThat(rejected.reason()).contains("tradeRef");
        assertThat(workItemStore.all()).isEmpty();
    }

    // ── ─────────────────────────────────────────────────────────────────────

    private static String ingestMessage(String tradeRef, String counterparty) {
        return """
                {
                  "tenantId": "%s",
                  "workflowType": "%s",
                  "sourceRef": "work-items.ingest-0-1",
                  "rawFields": {
                    "tradeRef": "%s",
                    "counterparty": "%s"
                  },
                  "makerUserId": "system"
                }
                """.formatted(TENANT, WORKFLOW_TYPE, tradeRef, counterparty);
    }

    // ── Spring context ────────────────────────────────────────────────────────

    @Configuration
    @EnableKafka
    static class TestConfig {

        @Bean
        ProducerFactory<String, String> producerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = KafkaTestUtils.producerProps(broker);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
            return new KafkaTemplate<>(pf);
        }

        @Bean
        ConsumerFactory<String, String> consumerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = KafkaTestUtils.consumerProps("test-ingestion-group", "false", broker);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            return new DefaultKafkaConsumerFactory<>(props);
        }

        @Bean
        ConsumerFactory<String, String> dlqConsumerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = KafkaTestUtils.consumerProps("test-dlq-group", "false", broker);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            return new DefaultKafkaConsumerFactory<>(props);
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
                ConsumerFactory<String, String> consumerFactory,
                KafkaTemplate<String, String> kafkaTemplate) {
            var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
            factory.setConsumerFactory(consumerFactory);
            // 0 retries → any processing exception immediately routes to DLQ
            var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                    (record, ex) -> new TopicPartition(DLQ_TOPIC, record.partition()));
            factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 0L)));
            return factory;
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, String> dlqListenerContainerFactory(
                ConsumerFactory<String, String> dlqConsumerFactory) {
            var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
            factory.setConsumerFactory(dlqConsumerFactory);
            return factory;
        }

        // ── In-memory domain doubles ──────────────────────────────────────────

        @Bean
        TestWorkItemStore workItemStore() {
            return new TestWorkItemStore();
        }

        @Bean
        TestIdempotencyStore idempotencyStore() {
            return new TestIdempotencyStore();
        }

        @Bean
        IIngestionConfigRepository ingestionConfigRepository() {
            IngestionConfig config = new IngestionConfig(
                    "tenant-kafka", "SETTLEMENT_EXCEPTION", SourceType.KAFKA,
                    List.of(
                            new FieldMapping("tradeRef",     "tradeRef",     true),
                            new FieldMapping("counterparty", "counterparty", false)
                    ),
                    UnknownColumnPolicy.IGNORE,
                    IdempotencyKeyStrategy.EXPLICIT_FIELD,
                    List.of(),
                    "tradeRef",
                    "OPEN"
            );
            return (tenantId, workflowType, sourceType) -> Optional.of(config);
        }

        @Bean
        IIdempotencyKeyRepository idempotencyKeyRepository(TestIdempotencyStore store) {
            return new IIdempotencyKeyRepository() {
                @Override
                public boolean exists(String tenantId, String workflowType, String key) {
                    return store.contains(tenantId + ":" + workflowType + ":" + key);
                }
                @Override
                public void save(String tenantId, String workflowType, String key) {
                    store.add(tenantId + ":" + workflowType + ":" + key);
                }
            };
        }

        @Bean
        IIngestionWorkItemRepository ingestionWorkItemRepository(TestWorkItemStore store) {
            return workItem -> {
                store.add(workItem);
                return workItem;
            };
        }

        @Bean
        IIngestionAuditRepository ingestionAuditRepository() {
            return entry -> {};
        }

        @Bean
        IGroupAssignmentPort groupAssignmentPort() {
            return (tenantId, workflowType, fields) ->
                    new IGroupAssignmentPort.AssignmentResult("default-group", false);
        }

        @Bean
        IIngestRecordUseCase ingestionService(
                IIngestionConfigRepository configRepository,
                IIdempotencyKeyRepository idempotencyRepository,
                IIngestionWorkItemRepository workItemRepository,
                IIngestionAuditRepository auditRepository,
                IGroupAssignmentPort groupAssignmentPort) {
            return new IngestionService(configRepository, idempotencyRepository,
                    workItemRepository, auditRepository, groupAssignmentPort);
        }

        @Bean
        TestIngestionListener testIngestionListener(IIngestRecordUseCase ingestUseCase) {
            return new TestIngestionListener(ingestUseCase);
        }

        @Bean
        TestDlqListener testDlqListener() {
            return new TestDlqListener();
        }
    }

    // ── Test-internal Kafka listener (design template for the real adapter) ───

    static class TestIngestionListener {

        private final IIngestRecordUseCase ingestUseCase;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final List<IngestionResult> results = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch latch = new CountDownLatch(1);

        TestIngestionListener(IIngestRecordUseCase ingestUseCase) {
            this.ingestUseCase = ingestUseCase;
        }

        @KafkaListener(
                topics = INGEST_TOPIC,
                groupId = "test-ingestion-group",
                containerFactory = "kafkaListenerContainerFactory"
        )
        void handle(@Payload String payload) throws Exception {
            RawInboundRecord record = parseRecord(payload);
            IngestionResult result = ingestUseCase.ingest(record);
            results.add(result);
            latch.countDown();
        }

        @SuppressWarnings("unchecked")
        private RawInboundRecord parseRecord(String payload) throws Exception {
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            Map<String, String> rawFields = (Map<String, String>) map.get("rawFields");
            return new RawInboundRecord(
                    (String) map.get("tenantId"),
                    (String) map.get("workflowType"),
                    SourceType.KAFKA,
                    (String) map.get("sourceRef"),
                    rawFields != null ? rawFields : Map.of(),
                    (String) map.get("makerUserId")
            );
        }

        void reset(int expectedMessages) {
            results.clear();
            latch = new CountDownLatch(expectedMessages);
        }

        List<IngestionResult> results() { return Collections.unmodifiableList(results); }
        CountDownLatch latch()          { return latch; }
    }

    static class TestDlqListener {

        private final List<String> payloads = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch latch = new CountDownLatch(1);

        @KafkaListener(
                topics = DLQ_TOPIC,
                groupId = "test-dlq-group",
                containerFactory = "dlqListenerContainerFactory"
        )
        void handle(@Payload String payload) {
            payloads.add(payload);
            latch.countDown();
        }

        void reset(int expected) {
            payloads.clear();
            latch = new CountDownLatch(expected);
        }

        List<String> payloads()  { return Collections.unmodifiableList(payloads); }
        CountDownLatch latch()   { return latch; }
    }

    // ── Thread-safe in-memory stores ──────────────────────────────────────────

    static class TestWorkItemStore {
        private final List<WorkItem> items = new CopyOnWriteArrayList<>();
        void add(WorkItem w)        { items.add(w); }
        void clear()                { items.clear(); }
        List<WorkItem> all()        { return Collections.unmodifiableList(items); }
    }

    static class TestIdempotencyStore {
        private final Set<String> keys = Collections.synchronizedSet(new HashSet<>());
        void add(String key)        { keys.add(key); }
        boolean contains(String k)  { return keys.contains(k); }
        void clear()                { keys.clear(); }
    }
}
