package com.platform.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.api.adapter.out.postgres.AuditEntryJdbcRepository;
import com.platform.api.adapter.out.postgres.EmbeddedPostgresProvider;
import com.platform.api.adapter.out.postgres.IdempotencyKeyJdbcRepository;
import com.platform.api.adapter.out.postgres.IngestionConfigJdbcRepository;
import com.platform.api.adapter.out.postgres.IngestionWorkItemJdbcRepository;
import com.platform.api.adapter.out.postgres.RoutingConfigJdbcRepository;
import com.platform.api.adapter.out.routing.RoutingGroupAssignmentAdapter;
import com.platform.domain.model.SourceType;
import com.platform.ingestion.domain.exception.GroupAssignmentException;
import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;
import com.platform.ingestion.domain.service.IngestionService;
import com.platform.routing.domain.exception.RoutingConfigNotFoundException;
import com.platform.routing.domain.service.RoutingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * End to end through the production adapters: ingestion asks the real routing engine, which reads
 * routing config from PostgreSQL, and the resulting work item and audit trail are read back from
 * the database. Only the clock is fixed and the event publisher is a no-op.
 */
class RoutingIngestionIntegrationTest {

  private static final String TENANT = "tenant-1";
  private static final String WORKFLOW = "SETTLEMENT_EXCEPTION";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

  private static final NamedParameterJdbcTemplate jdbc =
      new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
  private static final ObjectMapper objectMapper = new JsonMapper();

  private final AuditEntryJdbcRepository auditRepo =
      new AuditEntryJdbcRepository(jdbc, objectMapper);
  private final IngestionService ingestion =
      new IngestionService(
          new IngestionConfigJdbcRepository(jdbc, objectMapper),
          new IdempotencyKeyJdbcRepository(jdbc),
          new IngestionWorkItemJdbcRepository(jdbc, objectMapper),
          auditRepo,
          new RoutingGroupAssignmentAdapter(
              new RoutingService(
                  new RoutingConfigJdbcRepository(jdbc, objectMapper),
                  auditRepo,
                  new SimpleMeterRegistry(),
                  CLOCK)),
          event -> {},
          new SimpleMeterRegistry());

  @BeforeEach
  void resetAndSeedIngestionConfig() {
    jdbc.update("TRUNCATE work_items, audit_entries, config_documents CASCADE", Map.of());
    insertConfig("ing-1", "INGESTION_SOURCE_CONFIG", ingestionConfig());
  }

  @Test
  void aMatchingRuleAssignsTheWorkItemToItsTargetGroup() {
    insertConfig("rc-1", "ROUTING_CONFIG", routingConfig(seniorRule(null)));

    var created = ingest("T-1", "25000000");

    assertThat(created.workItem().assignedGroup()).isEqualTo("group-senior");
    assertThat(created.workItem().routedByDefault()).isFalse();
    assertThat(storedGroup(created.workItem().id())).isEqualTo("group-senior");
  }

  @Test
  void noMatchingRuleFallsBackToTheConfigsOwnDefaultGroup() {
    insertConfig("rc-1", "ROUTING_CONFIG", routingConfig(seniorRule(null)));

    var created = ingest("T-2", "5000");

    assertThat(created.workItem().assignedGroup()).isEqualTo("group-ops");
    assertThat(created.workItem().routedByDefault()).isTrue();
    assertThat(storedRoutedByDefault(created.workItem().id())).isTrue();
  }

  @Test
  void anExpiredRuleIsSkippedAndTheNextOutcomeAppliesInstead() {
    // The clock is fixed at 2026-06-15; the rule ended the day before.
    insertConfig("rc-1", "ROUTING_CONFIG", routingConfig(seniorRule("2026-06-14")));

    var created = ingest("T-3", "25000000");

    assertThat(created.workItem().assignedGroup()).isEqualTo("group-ops");
    assertThat(created.workItem().routedByDefault()).isTrue();
  }

  @Test
  void theRoutingAuditEntryRefersToTheSameWorkItemAsTheIngestionAuditEntry() {
    insertConfig("rc-1", "ROUTING_CONFIG", routingConfig(seniorRule(null)));

    var created = ingest("T-4", "25000000");
    String workItemId = created.workItem().id();

    List<String> eventTypes = auditEventTypesFor(workItemId);
    assertThat(eventTypes).contains("ASSIGNMENT", "INGESTION");
  }

  @Test
  void aFallbackIsAuditedAsRoutingFallbackAgainstTheWorkItem() {
    insertConfig("rc-1", "ROUTING_CONFIG", routingConfig(seniorRule(null)));

    var created = ingest("T-5", "5000");

    assertThat(auditEventTypesFor(created.workItem().id())).contains("ROUTING_FALLBACK");
  }

  @Test
  void withNoRoutingConfigIngestionFailsAndPersistsNothing() {
    assertThatThrownBy(() -> ingestResult("T-6", "25000000"))
        .isInstanceOf(GroupAssignmentException.class)
        .hasRootCauseInstanceOf(RoutingConfigNotFoundException.class);

    assertThat(workItemCount()).isZero();
  }

  @Test
  void aRecordRejectedForMissingConfigCanBeReplayedOnceConfigExists() {
    assertThatThrownBy(() -> ingestResult("T-7", "25000000"))
        .isInstanceOf(GroupAssignmentException.class)
        .hasRootCauseInstanceOf(RoutingConfigNotFoundException.class);

    insertConfig("rc-1", "ROUTING_CONFIG", routingConfig(seniorRule(null)));
    var replayed = ingest("T-7", "25000000");

    assertThat(replayed.workItem().assignedGroup()).isEqualTo("group-senior");
    assertThat(workItemCount()).isEqualTo(1);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private IngestionResult.Created ingest(String tradeRef, String notional) {
    IngestionResult result = ingestResult(tradeRef, notional);
    assertThat(result).isInstanceOf(IngestionResult.Created.class);
    return (IngestionResult.Created) result;
  }

  private IngestionResult ingestResult(String tradeRef, String notional) {
    return ingestion.ingest(
        new RawInboundRecord(
            TENANT,
            WORKFLOW,
            SourceType.KAFKA,
            "ref-" + tradeRef,
            Map.of("tradeRef", tradeRef, "notionalAmount", notional),
            "maker-1"));
  }

  private static Map<String, Object> ingestionConfig() {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("tenantId", TENANT);
    content.put("workflowType", WORKFLOW);
    content.put("sourceType", "KAFKA");
    content.put(
        "fieldMappings",
        List.of(
            Map.of("sourceField", "tradeRef", "targetField", "trade.ref", "required", true),
            Map.of(
                "sourceField",
                "notionalAmount",
                "targetField",
                "trade.notionalAmount.amount",
                "required",
                false)));
    content.put("unknownColumnPolicy", "IGNORE");
    content.put("idempotencyKeyStrategy", "EXPLICIT_FIELD");
    content.put("idempotencyKeyFields", List.of());
    content.put("idempotencyExplicitField", "tradeRef");
    content.put("initialState", "UNDER_REVIEW");
    return content;
  }

  private static Map<String, Object> routingConfig(Map<String, Object> rule) {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("id", "rc-1");
    content.put("tenantId", TENANT);
    content.put("workflowType", WORKFLOW);
    content.put("defaultGroup", "group-ops");
    content.put("alertOnDefault", false);
    content.put("rules", List.of(rule));
    return content;
  }

  private static Map<String, Object> seniorRule(String effectiveTo) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("id", "r-senior");
    rule.put("name", "High notional");
    rule.put("priority", 1);
    rule.put("targetGroup", "group-senior");
    rule.put("active", true);
    rule.put(
        "conditions",
        Map.of(
            "type",
            "LEAF",
            "field",
            "trade.notionalAmount.amount",
            "operator",
            "GTE",
            "value",
            "10000000"));
    if (effectiveTo != null) {
      rule.put("effectiveTo", effectiveTo);
    }
    return rule;
  }

  private static void insertConfig(String id, String configType, Map<String, Object> content) {
    jdbc.update(
        """
        INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
        VALUES (:id, :tenant, :workflow, :configType, CAST(:content AS jsonb), '1', true)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenant", TENANT)
            .addValue("workflow", WORKFLOW)
            .addValue("configType", configType)
            .addValue("content", toJson(content)));
  }

  private static String toJson(Map<String, Object> content) {
    try {
      return objectMapper.writeValueAsString(content);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String storedGroup(String workItemId) {
    return jdbc.queryForObject(
        "SELECT assigned_group FROM work_items WHERE id = :id",
        Map.of("id", workItemId),
        String.class);
  }

  private static boolean storedRoutedByDefault(String workItemId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT routed_by_default FROM work_items WHERE id = :id",
            Map.of("id", workItemId),
            Boolean.class));
  }

  private static List<String> auditEventTypesFor(String workItemId) {
    return new ArrayList<>(
        jdbc.queryForList(
            "SELECT event_type FROM audit_entries WHERE work_item_id = :id",
            Map.of("id", workItemId),
            String.class));
  }

  private static int workItemCount() {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM work_items", Map.of(), Integer.class);
    return count == null ? 0 : count;
  }
}
