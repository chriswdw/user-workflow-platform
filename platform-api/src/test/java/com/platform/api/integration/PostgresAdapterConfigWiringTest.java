package com.platform.api.integration;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.platform.api.adapter.out.kafka.KafkaDomainEventPublisher;
import com.platform.api.adapter.out.postgres.EmbeddedPostgresProvider;
import com.platform.api.config.DomainEventKafkaConfig;
import com.platform.api.config.PostgresAdapterConfig;
import com.platform.audit.domain.service.AuditService;
import com.platform.config.domain.service.ConfigService;
import com.platform.config.domain.service.SourceConnectionService;
import com.platform.config.domain.service.SubmissionCreationService;
import com.platform.domain.ports.out.IDomainEventPublisher;
import com.platform.workflow.domain.model.TransitionCommand;
import com.platform.workflow.domain.service.WorkflowService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real {@link PostgresAdapterConfig} and {@link DomainEventKafkaConfig}
 * production wiring against embedded PostgreSQL and embedded Kafka — the one
 * conditional-config wiring path with no coverage elsewhere. Neither class is
 * touched by the Cucumber @SpringBootTest context (which runs under profile
 * "test" with no datasource/kafka properties, so DevConfig's no-op fallbacks
 * win instead) nor by any hand-rolled test config.
 */
@SpringJUnitConfig(classes = PostgresAdapterConfigWiringTest.TestConfig.class)
@EmbeddedKafka(partitions = 1, topics = {PostgresAdapterConfigWiringTest.DOMAIN_EVENTS_TOPIC})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://embedded/test",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "platform.events.topic=" + PostgresAdapterConfigWiringTest.DOMAIN_EVENTS_TOPIC
})
class PostgresAdapterConfigWiringTest {

    static final String DOMAIN_EVENTS_TOPIC = "platform.domain-events";

    private static final String TENANT        = "tenant-wiring";
    private static final String WORKFLOW_TYPE = "SETTLEMENT_EXCEPTION";

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new JsonMapper();

    @Autowired private IDomainEventPublisher domainEventPublisher;
    @Autowired private WorkflowService workflowService;
    @Autowired private ConfigService configService;
    @Autowired private AuditService auditService;
    @Autowired private SubmissionCreationService submissionCreationService;
    @Autowired private SourceConnectionService sourceConnectionService;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, String> domainEventConsumer;

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE work_items, audit_entries, config_documents", Map.of());
        domainEventConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
                KafkaTestUtils.consumerProps(embeddedKafkaBroker, "wiring-test-group", true),
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer());
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(domainEventConsumer, DOMAIN_EVENTS_TOPIC);
    }

    @AfterEach
    void closeConsumer() {
        domainEventConsumer.close();
    }

    // ── Real adapters wire in, not DevConfig fallbacks ────────────────────────

    @Test
    void realAdaptersWireIn_notDevConfigFallbacks() {
        assertThat(domainEventPublisher).isInstanceOf(KafkaDomainEventPublisher.class);
        assertThat(workflowService).isNotNull();
        assertThat(configService).isNotNull();
        assertThat(auditService).isNotNull();
        assertThat(submissionCreationService).isNotNull();
        assertThat(sourceConnectionService).isNotNull();
    }

    // ── PostgresAdapterConfig's WorkflowService is genuinely wired to DomainEventKafkaConfig ──

    @Test
    void transition_publishesDomainEventThroughRealWiring() {
        insertWorkItem("wi-wiring-1", "OPEN");
        insertWorkflowConfig("wf-wiring", closeTransitionConfig());

        workflowService.transition(new TransitionCommand(
                "wi-wiring-1", TENANT, "close", "analyst-1", "ANALYST", Map.of()));

        var record = KafkaTestUtils.getSingleRecord(domainEventConsumer, DOMAIN_EVENTS_TOPIC,
                java.time.Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo("wi-wiring-1");
        assertThat(record.value())
                .contains("\"eventType\":\"STATE_TRANSITION\"")
                .contains("\"workItemId\":\"wi-wiring-1\"");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static String closeTransitionConfig() {
        return """
                {
                  "id": "wf-dynamic", "tenantId": "%s", "workflowType": "%s",
                  "initialState": "OPEN", "active": true,
                  "states": [
                    {"name": "OPEN",   "terminal": false, "allowedRoles": ["ANALYST"]},
                    {"name": "CLOSED", "terminal": true,  "allowedRoles": ["ANALYST"]}
                  ],
                  "transitions": [
                    {
                      "name": "close",
                      "fromState": "OPEN", "toState": "CLOSED",
                      "trigger": "USER_ACTION",
                      "allowedRoles": ["ANALYST"],
                      "requiresMakerChecker": false,
                      "actions": [], "validationRules": []
                    }
                  ]
                }
                """.formatted(TENANT, WORKFLOW_TYPE);
    }

    private static void insertWorkItem(String id, String status) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO work_items
                  (id, tenant_id, workflow_type, correlation_id, config_version_id,
                   source, source_ref, idempotency_key, status, assigned_group,
                   routed_by_default, fields, priority_score, priority_level,
                   priority_last_calculated_at, pending_checker_id, pending_checker_transition,
                   version, maker_user_id, created_at, updated_at)
                VALUES
                  (:id, :tenantId, :workflowType, :corrId, null,
                   'KAFKA', :src, :idem, :status, 'ops-team',
                   false, CAST('{}' AS jsonb), null, null,
                   null, null, null,
                   1, 'system', :now, :now)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", TENANT)
                        .addValue("workflowType", WORKFLOW_TYPE)
                        .addValue("corrId", "corr-" + id)
                        .addValue("src", "src-" + id)
                        .addValue("idem", "idem-" + id)
                        .addValue("status", status)
                        .addValue("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)));
    }

    private static void insertWorkflowConfig(String id, String contentJson) {
        jdbc.update("""
                INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
                VALUES (:id, :tenantId, :workflowType, 'WORKFLOW_CONFIG', CAST(:content AS jsonb), '1', true)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", TENANT)
                        .addValue("workflowType", WORKFLOW_TYPE)
                        .addValue("content", contentJson));
    }

    @Configuration
    @Import({PostgresAdapterConfig.class, DomainEventKafkaConfig.class})
    static class TestConfig {

        @Bean
        NamedParameterJdbcTemplate jdbc() {
            return new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
        }

        @Bean
        ObjectMapper objectMapper() { return new JsonMapper(); }

        @Bean
        MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }
}
