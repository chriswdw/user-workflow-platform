package com.platform.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.api.adapter.out.postgres.AuditEntryJdbcRepository;
import com.platform.api.adapter.out.postgres.EmbeddedPostgresProvider;
import com.platform.api.config.IngestionAdapterConfig;
import com.platform.api.config.KafkaIngestionConfig;
import com.platform.domain.ports.out.IDomainEventPublisher;
import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;
import com.platform.ingestion.domain.ports.in.IIngestRecordUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end test: real Kafka broker + real PostgreSQL (embedded), no mocks. Verifies that a
 * message sent to the ingest topic produces a work_items row, using the real {@link
 * IngestionAdapterConfig} and {@link KafkaIngestionConfig} production wiring.
 */
@SpringJUnitConfig(classes = KafkaIngestionEndToEndTest.TestConfig.class)
@EmbeddedKafka(
    partitions = 1,
    topics = {KafkaIngestionEndToEndTest.INGEST_TOPIC, KafkaIngestionEndToEndTest.DLQ_TOPIC})
@TestPropertySource(
    properties = {
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "spring.datasource.url=jdbc:postgresql://embedded/test"
    })
class KafkaIngestionEndToEndTest {

  static final String INGEST_TOPIC = "work-items.ingest";
  static final String DLQ_TOPIC = "work-items.ingest.dlq";

  private static final String TENANT = "tenant-e2e";
  private static final String WORKFLOW_TYPE = "SETTLEMENT_EXCEPTION";

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private ResultCaptor resultCaptor;
  @Autowired private NamedParameterJdbcTemplate jdbc;
  @Autowired private EmbeddedKafkaBroker broker;

  @BeforeEach
  void resetDb() {
    jdbc.update("TRUNCATE work_items CASCADE", Map.of());
    jdbc.update("TRUNCATE audit_entries CASCADE", Map.of());
    insertIngestionConfig("e2e-ing-cfg", TENANT); // idempotent — ON CONFLICT DO NOTHING
    insertRoutingConfig(); // idempotent — ON CONFLICT DO NOTHING
    resultCaptor.reset(1);
  }

  // ── Valid message → work item row appears in PostgreSQL ───────────────────

  @Test
  void validMessage_producesWorkItemRowInPostgres() throws Exception {
    kafkaTemplate.send(INGEST_TOPIC, message("TRD-E2E-001", "ACME Corp")).get(5, TimeUnit.SECONDS);

    assertThat(resultCaptor.latch().await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(resultCaptor.results().get(0)).isInstanceOf(IngestionResult.Created.class);

    Integer count =
        jdbc.queryForObject(
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

    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM work_items WHERE tenant_id = :t AND idempotency_key = :k",
            new MapSqlParameterSource().addValue("t", TENANT).addValue("k", "TRD-E2E-DUP"),
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  // ── Ingestion audit entry is written alongside the work item ─────────────

  @Test
  void validMessage_writesIngestionAuditEntry() throws Exception {
    kafkaTemplate
        .send(INGEST_TOPIC, message("TRD-E2E-AUDIT", "ACME Corp"))
        .get(5, TimeUnit.SECONDS);

    assertThat(resultCaptor.latch().await(10, TimeUnit.SECONDS)).isTrue();

    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_entries WHERE tenant_id = :t AND event_type = 'INGESTION'",
            new MapSqlParameterSource().addValue("t", TENANT),
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  // ── Routing: the tenant's routing config decides the group ───────────────

  @Test
  void messageMatchingARoutingRule_isAssignedToTheRulesGroup() throws Exception {
    kafkaTemplate.send(INGEST_TOPIC, message("TRD-E2E-VIP", "VIP Corp")).get(5, TimeUnit.SECONDS);

    assertThat(resultCaptor.latch().await(10, TimeUnit.SECONDS)).isTrue();

    assertThat(storedGroup("TRD-E2E-VIP")).isEqualTo("group-e2e-vip");
    assertThat(storedRoutedByDefault("TRD-E2E-VIP")).isFalse();
    assertThat(auditEventTypesForWorkItemOf("TRD-E2E-VIP")).contains("ASSIGNMENT", "INGESTION");
  }

  @Test
  void messageMatchingNoRule_isAssignedToTheConfigsDefaultGroup() throws Exception {
    kafkaTemplate.send(INGEST_TOPIC, message("TRD-E2E-ACME", "ACME Corp")).get(5, TimeUnit.SECONDS);

    assertThat(resultCaptor.latch().await(10, TimeUnit.SECONDS)).isTrue();

    assertThat(storedGroup("TRD-E2E-ACME")).isEqualTo("group-e2e-default");
    assertThat(storedRoutedByDefault("TRD-E2E-ACME")).isTrue();
    assertThat(auditEventTypesForWorkItemOf("TRD-E2E-ACME")).contains("ROUTING_FALLBACK");
  }

  @Test
  void messageForATenantWithNoRoutingConfig_isDeadLetteredAndCreatesNoWorkItem() throws Exception {
    String unroutedTenant = "tenant-e2e-unrouted";
    insertIngestionConfig("e2e-ing-cfg-unrouted", unroutedTenant); // ingestion config, no routing

    try (Consumer<String, String> dlq =
        new KafkaConsumer<>(
            KafkaTestUtils.consumerProps(broker, "routing-dlq-" + UUID.randomUUID(), true),
            new StringDeserializer(),
            new StringDeserializer())) {
      broker.consumeFromAnEmbeddedTopic(dlq, DLQ_TOPIC);

      kafkaTemplate
          .send(INGEST_TOPIC, messageFor(unroutedTenant, "TRD-E2E-NOROUTE", "ACME Corp"))
          .get(5, TimeUnit.SECONDS);

      ConsumerRecord<String, String> dead =
          KafkaTestUtils.getSingleRecord(dlq, DLQ_TOPIC, Duration.ofSeconds(15));
      assertThat(dead.value()).contains("TRD-E2E-NOROUTE");
      assertThat(headerText(dead)).contains("RoutingConfigNotFoundException");
    }

    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM work_items WHERE tenant_id = :t",
            new MapSqlParameterSource().addValue("t", unroutedTenant),
            Integer.class);
    assertThat(count).isZero();
  }

  // ── ─────────────────────────────────────────────────────────────────────

  private static String message(String tradeRef, String counterparty) {
    return messageFor(TENANT, tradeRef, counterparty);
  }

  private static String messageFor(String tenant, String tradeRef, String counterparty) {
    return """
    {
      "tenantId": "%s",
      "workflowType": "%s",
      "sourceRef": "e2e-topic-0-1",
      "rawFields": { "tradeRef": "%s", "counterparty": "%s" },
      "makerUserId": "e2e-system"
    }
    """
        .formatted(tenant, WORKFLOW_TYPE, tradeRef, counterparty);
  }

  private String storedGroup(String tradeRef) {
    return jdbc.queryForObject(
        "SELECT assigned_group FROM work_items WHERE tenant_id = :t AND idempotency_key = :k",
        new MapSqlParameterSource().addValue("t", TENANT).addValue("k", tradeRef),
        String.class);
  }

  private boolean storedRoutedByDefault(String tradeRef) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT routed_by_default FROM work_items WHERE tenant_id = :t AND idempotency_key = :k",
            new MapSqlParameterSource().addValue("t", TENANT).addValue("k", tradeRef),
            Boolean.class));
  }

  private List<String> auditEventTypesForWorkItemOf(String tradeRef) {
    return jdbc.queryForList(
        """
        SELECT a.event_type FROM audit_entries a
        JOIN work_items w ON w.id = a.work_item_id
        WHERE w.tenant_id = :t AND w.idempotency_key = :k
        """,
        new MapSqlParameterSource().addValue("t", TENANT).addValue("k", tradeRef),
        String.class);
  }

  private static String headerText(ConsumerRecord<String, String> record) {
    StringBuilder text = new StringBuilder();
    record
        .headers()
        .forEach(h -> text.append(new String(h.value(), StandardCharsets.UTF_8)).append('\n'));
    return text.toString();
  }

  private void insertRoutingConfig() {
    String content =
        """
        {
          "id": "e2e-routing-cfg",
          "tenantId": "%s",
          "workflowType": "%s",
          "defaultGroup": "group-e2e-default",
          "alertOnDefault": false,
          "rules": [
            {
              "id": "e2e-rule-vip",
              "name": "VIP counterparties",
              "priority": 1,
              "active": true,
              "targetGroup": "group-e2e-vip",
              "conditions": {"type": "LEAF", "field": "counterparty.name", "operator": "EQ", "value": "VIP Corp"}
            }
          ]
        }
        """
            .formatted(TENANT, WORKFLOW_TYPE);
    jdbc.update(
        """
        INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
        VALUES ('e2e-routing-cfg', :t, :w, 'ROUTING_CONFIG', CAST(:c AS jsonb), '1', true)
        ON CONFLICT (id) DO NOTHING
        """,
        new MapSqlParameterSource()
            .addValue("t", TENANT)
            .addValue("w", WORKFLOW_TYPE)
            .addValue("c", content));
  }

  private void insertIngestionConfig(String id, String tenant) {
    String content =
        """
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
        """
            .formatted(tenant, WORKFLOW_TYPE);
    jdbc.update(
        """
        INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
        VALUES (:id, :t, :w, 'INGESTION_SOURCE_CONFIG', CAST(:c AS jsonb), '1', true)
        ON CONFLICT (id) DO NOTHING
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("t", tenant)
            .addValue("w", WORKFLOW_TYPE)
            .addValue("c", content));
  }

  // ── Spring context — real JDBC adapters + embedded Kafka ─────────────────

  @Configuration
  @EnableKafka
  @Import({IngestionAdapterConfig.class, KafkaIngestionConfig.class})
  static class TestConfig {

    @Bean
    NamedParameterJdbcTemplate jdbc() {
      return new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new JsonMapper();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    AuditEntryJdbcRepository auditEntryJdbcRepository(
        NamedParameterJdbcTemplate jdbc, ObjectMapper om) {
      return new AuditEntryJdbcRepository(jdbc, om);
    }

    @Bean
    IDomainEventPublisher domainEventPublisher() {
      return event -> {};
    }

    @Bean
    @Primary
    ResultCaptor resultCaptor(IIngestRecordUseCase ingestUseCase) {
      return new ResultCaptor(ingestUseCase);
    }

    @Bean
    KafkaTemplate<String, String> kafkaTemplate(EmbeddedKafkaBroker broker) {
      return new KafkaTemplate<>(
          new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(broker)));
    }
  }

  // ── Wraps IIngestRecordUseCase to synchronise test assertions ─────────────

  static class ResultCaptor implements IIngestRecordUseCase {

    private final IIngestRecordUseCase delegate;
    private final List<IngestionResult> results = new CopyOnWriteArrayList<>();
    private volatile CountDownLatch latch = new CountDownLatch(1);

    ResultCaptor(IIngestRecordUseCase delegate) {
      this.delegate = delegate;
    }

    @Override
    public IngestionResult ingest(RawInboundRecord inboundRecord) {
      IngestionResult result = delegate.ingest(inboundRecord);
      results.add(result);
      latch.countDown();
      return result;
    }

    void reset(int n) {
      results.clear();
      latch = new CountDownLatch(n);
    }

    List<IngestionResult> results() {
      return Collections.unmodifiableList(results);
    }

    CountDownLatch latch() {
      return latch;
    }
  }
}
