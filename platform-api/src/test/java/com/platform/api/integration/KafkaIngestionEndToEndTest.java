package com.platform.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.api.adapter.in.kafka.WorkItemKafkaConsumer;
import com.platform.api.adapter.out.postgres.AuditEntryJdbcRepository;
import com.platform.api.adapter.out.postgres.EmbeddedPostgresProvider;
import com.platform.api.adapter.out.postgres.IdempotencyKeyJdbcRepository;
import com.platform.api.adapter.out.postgres.IngestionConfigJdbcRepository;
import com.platform.api.adapter.out.postgres.IngestionWorkItemJdbcRepository;
import com.platform.domain.model.SourceType;
import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;
import com.platform.ingestion.domain.ports.in.IIngestRecordUseCase;
import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;
import com.platform.ingestion.domain.service.IngestionService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test: real Kafka broker + real PostgreSQL (embedded), no mocks.
 * Verifies that a message sent to the ingest topic produces a work_items row.
 */
@SpringJUnitConfig(classes = KafkaIngestionEndToEndTest.TestConfig.class)
@EmbeddedKafka(
        partitions = 1,
        topics = {KafkaIngestionEndToEndTest.INGEST_TOPIC, KafkaIngestionEndToEndTest.DLQ_TOPIC}
)
class KafkaIngestionEndToEndTest {

    static final String INGEST_TOPIC = "work-items.ingest";
    static final String DLQ_TOPIC    = "work-items.ingest.dlq";

    private static final String TENANT        = "tenant-e2e";
    private static final String WORKFLOW_TYPE = "SETTLEMENT_EXCEPTION";

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ResultCaptor resultCaptor;
    @Autowired private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void resetDb() {
        jdbc.update("TRUNCATE work_items CASCADE", Map.of());
        jdbc.update("TRUNCATE audit_entries CASCADE", Map.of());
        insertIngestionConfig();  // idempotent — ON CONFLICT DO NOTHING
        resultCaptor.reset(1);
    }

    // ── Valid message → work item row appears in PostgreSQL ───────────────────

    @Test
    void validMessage_producesWorkItemRowInPostgres() throws Exception {
        kafkaTemplate.send(INGEST_TOPIC, message("TRD-E2E-001", "ACME Corp")).get(5, TimeUnit.SECONDS);

        assertThat(resultCaptor.latch().await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(resultCaptor.results().get(0)).isInstanceOf(IngestionResult.Created.class);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_items WHERE tenant_id = :t AND idempotency_key = :k",
                new MapSqlParameterSource().addValue("t", TENANT).addValue("k", "TRD-E2E-001"),
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── Duplicate message → second message does not create a second row ───────

    @Test
    void duplicateMessage_doesNotProduceSecondWorkItemRow() throws Exception {
        resultCaptor.reset(2);
        String msg = message("TRD-E2E-DUP", "ACME Corp");

        kafkaTemplate.send(INGEST_TOPIC, msg).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send(INGEST_TOPIC, msg).get(5, TimeUnit.SECONDS);

        assertThat(resultCaptor.latch().await(10, TimeUnit.SECONDS)).isTrue();

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_items WHERE tenant_id = :t AND idempotency_key = :k",
                new MapSqlParameterSource().addValue("t", TENANT).addValue("k", "TRD-E2E-DUP"),
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── Ingestion audit entry is written alongside the work item ─────────────

    @Test
    void validMessage_writesIngestionAuditEntry() throws Exception {
        kafkaTemplate.send(INGEST_TOPIC, message("TRD-E2E-AUDIT", "ACME Corp")).get(5, TimeUnit.SECONDS);

        assertThat(resultCaptor.latch().await(10, TimeUnit.SECONDS)).isTrue();

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_entries WHERE tenant_id = :t AND event_type = 'INGESTION'",
                new MapSqlParameterSource().addValue("t", TENANT),
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── ─────────────────────────────────────────────────────────────────────

    private static String message(String tradeRef, String counterparty) {
        return """
                {
                  "tenantId": "%s",
                  "workflowType": "%s",
                  "sourceRef": "e2e-topic-0-1",
                  "rawFields": { "tradeRef": "%s", "counterparty": "%s" },
                  "makerUserId": "e2e-system"
                }
                """.formatted(TENANT, WORKFLOW_TYPE, tradeRef, counterparty);
    }

    private void insertIngestionConfig() {
        String content = """
                {
                  "tenantId": "%s",
                  "workflowType": "%s",
                  "sourceType": "KAFKA",
                  "fieldMappings": [
                    {"sourceField": "tradeRef",     "targetField": "trade.ref",        "required": true},
                    {"sourceField": "counterparty",  "targetField": "counterparty.name", "required": false}
                  ],
                  "unknownColumnPolicy": "IGNORE",
                  "idempotencyKeyStrategy": "EXPLICIT_FIELD",
                  "idempotencyKeyFields": [],
                  "idempotencyExplicitField": "tradeRef",
                  "initialState": "UNDER_REVIEW"
                }
                """.formatted(TENANT, WORKFLOW_TYPE);
        jdbc.update("""
                INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
                VALUES ('e2e-ing-cfg', :t, :w, 'INGESTION_SOURCE_CONFIG', CAST(:c AS jsonb), '1', true)
                ON CONFLICT (id) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("t", TENANT)
                        .addValue("w", WORKFLOW_TYPE)
                        .addValue("c", content));
    }

    // ── Spring context — real JDBC adapters + embedded Kafka ─────────────────

    @Configuration
    @EnableKafka
    static class TestConfig {

        @Bean
        NamedParameterJdbcTemplate jdbc() {
            return new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
        }

        @Bean
        ObjectMapper objectMapper() { return new ObjectMapper(); }

        @Bean
        IngestionConfigJdbcRepository ingestionConfigRepo(NamedParameterJdbcTemplate jdbc, ObjectMapper om) {
            return new IngestionConfigJdbcRepository(jdbc, om);
        }

        @Bean
        IdempotencyKeyJdbcRepository idempotencyKeyRepo(NamedParameterJdbcTemplate jdbc) {
            return new IdempotencyKeyJdbcRepository(jdbc);
        }

        @Bean
        IngestionWorkItemJdbcRepository ingestionWorkItemRepo(NamedParameterJdbcTemplate jdbc, ObjectMapper om) {
            return new IngestionWorkItemJdbcRepository(jdbc, om);
        }

        @Bean
        AuditEntryJdbcRepository auditEntryRepo(NamedParameterJdbcTemplate jdbc, ObjectMapper om) {
            return new AuditEntryJdbcRepository(jdbc, om);
        }

        @Bean
        IGroupAssignmentPort groupAssignmentPort() {
            return (tenantId, workflowType, fields) ->
                    new IGroupAssignmentPort.AssignmentResult("group-ops", false);
        }

        @Bean
        IIngestRecordUseCase ingestionService(
                IngestionConfigJdbcRepository configRepo,
                IdempotencyKeyJdbcRepository idempotencyRepo,
                IngestionWorkItemJdbcRepository workItemRepo,
                AuditEntryJdbcRepository auditRepo,
                IGroupAssignmentPort groupPort) {
            return new IngestionService(configRepo, idempotencyRepo, workItemRepo, auditRepo, groupPort);
        }

        @Bean
        ResultCaptor resultCaptor(IIngestRecordUseCase ingestUseCase) {
            return new ResultCaptor(ingestUseCase);
        }

        @Bean
        WorkItemKafkaConsumer workItemKafkaConsumer(ResultCaptor captor, ObjectMapper om) {
            return new WorkItemKafkaConsumer(captor, om);
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate(EmbeddedKafkaBroker broker) {
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                    KafkaTestUtils.producerProps(broker)));
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, String> ingestionKafkaListenerContainerFactory(
                EmbeddedKafkaBroker broker, KafkaTemplate<String, String> kafkaTemplate) {
            var consumerFactory = new DefaultKafkaConsumerFactory<>(
                    KafkaTestUtils.consumerProps("e2e-group", "false", broker),
                    new org.apache.kafka.common.serialization.StringDeserializer(),
                    new org.apache.kafka.common.serialization.StringDeserializer());
            var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                    (record, ex) -> new TopicPartition(DLQ_TOPIC, record.partition()));
            var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
            factory.setConsumerFactory(consumerFactory);
            factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 0L)));
            return factory;
        }
    }

    // ── Wraps IIngestRecordUseCase to synchronise test assertions ─────────────

    static class ResultCaptor implements IIngestRecordUseCase {

        private final IIngestRecordUseCase delegate;
        private final List<IngestionResult> results = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch latch = new CountDownLatch(1);

        ResultCaptor(IIngestRecordUseCase delegate) { this.delegate = delegate; }

        @Override
        public IngestionResult ingest(RawInboundRecord record) {
            IngestionResult result = delegate.ingest(record);
            results.add(result);
            latch.countDown();
            return result;
        }

        void reset(int n) { results.clear(); latch = new CountDownLatch(n); }
        List<IngestionResult> results() { return Collections.unmodifiableList(results); }
        CountDownLatch latch() { return latch; }
    }
}
