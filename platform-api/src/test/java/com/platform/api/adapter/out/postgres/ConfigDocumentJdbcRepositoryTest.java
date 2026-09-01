package com.platform.api.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ConfigDocumentJdbcRepositoryTest {

  private static final NamedParameterJdbcTemplate jdbc =
      new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
  private static final ObjectMapper objectMapper = new JsonMapper();

  private final ConfigDocumentJdbcRepository repository =
      new ConfigDocumentJdbcRepository(jdbc, objectMapper);

  @BeforeEach
  void truncate() {
    jdbc.update("TRUNCATE config_documents", Map.of());
  }

  @Test
  void findByTenantAndWorkflowTypeAndType_returnsEmptyWhenNoneMatch() {
    List<ConfigDocument> result =
        repository.findByTenantAndWorkflowTypeAndType(
            "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG);
    assertThat(result).isEmpty();
  }

  @Test
  void findByTenantAndWorkflowTypeAndType_returnsMatchingDocuments() {
    insert(
        "doc-1",
        "tenant-1",
        "SETTLEMENT_EXCEPTION",
        ConfigType.WORKFLOW_CONFIG,
        Map.of("key", "value"),
        true);
    insert(
        "doc-2",
        "tenant-1",
        "SETTLEMENT_EXCEPTION",
        ConfigType.DETAIL_VIEW_CONFIG,
        Map.of("other", "data"),
        true);

    List<ConfigDocument> result =
        repository.findByTenantAndWorkflowTypeAndType(
            "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo("doc-1");
    assertThat(result.get(0).configType()).isEqualTo(ConfigType.WORKFLOW_CONFIG);
    assertThat(result.get(0).content()).containsKey("key");
  }

  @Test
  void findByTenantAndWorkflowTypeAndType_returnsAllActiveAndInactive() {
    insert(
        "doc-active",
        "tenant-1",
        "SETTLEMENT_EXCEPTION",
        ConfigType.WORKFLOW_CONFIG,
        Map.of("v", "1"),
        true);
    insert(
        "doc-inactive",
        "tenant-1",
        "SETTLEMENT_EXCEPTION",
        ConfigType.WORKFLOW_CONFIG,
        Map.of("v", "0"),
        false);

    List<ConfigDocument> result =
        repository.findByTenantAndWorkflowTypeAndType(
            "tenant-1", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG);

    assertThat(result)
        .hasSize(2)
        .extracting(ConfigDocument::id)
        .containsExactlyInAnyOrder("doc-active", "doc-inactive");
  }

  @Test
  void findAllActiveByTenant_returnsOnlyActiveDocuments() {
    insert(
        "doc-active",
        "tenant-1",
        "SETTLEMENT_EXCEPTION",
        ConfigType.WORKFLOW_CONFIG,
        Map.of("v", "1"),
        true);
    insert(
        "doc-inactive",
        "tenant-1",
        "SETTLEMENT_EXCEPTION",
        ConfigType.DETAIL_VIEW_CONFIG,
        Map.of("v", "0"),
        false);

    List<ConfigDocument> result = repository.findAllActiveByTenant("tenant-1");

    assertThat(result).hasSize(1).extracting(ConfigDocument::id).containsExactly("doc-active");
  }

  @Test
  void findAllActiveByTenant_doesNotCrossTenantBoundary() {
    insert("doc-A", "tenant-A", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG, Map.of(), true);
    insert("doc-B", "tenant-B", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_CONFIG, Map.of(), true);

    assertThat(repository.findAllActiveByTenant("tenant-A"))
        .extracting(ConfigDocument::id)
        .containsExactly("doc-A");
  }

  @Test
  void findAllActiveByTenant_throwsIllegalStateWhenContentIsNotAJsonObject() {
    // content is valid JSONB but not a JSON object — simulates data corruption upstream of
    // this adapter, since this query has no filter on the content column's shape.
    jdbc.update(
        """
                INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
                VALUES (:id, :tenantId, :workflowType, :configType, CAST(:content AS jsonb), '1', true)
                """,
        new MapSqlParameterSource()
            .addValue("id", "doc-corrupt")
            .addValue("tenantId", "tenant-1")
            .addValue("workflowType", "SETTLEMENT_EXCEPTION")
            .addValue("configType", ConfigType.WORKFLOW_CONFIG.name())
            .addValue("content", "[\"not\", \"an\", \"object\"]"));

    assertThatThrownBy(() -> repository.findAllActiveByTenant("tenant-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to deserialise config document content from JSONB");
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private static void insert(
      String id,
      String tenantId,
      String workflowType,
      ConfigType configType,
      Map<String, Object> content,
      boolean active) {
    try {
      String json = objectMapper.writeValueAsString(content);
      jdbc.update(
          """
                    INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active)
                    VALUES (:id, :tenantId, :workflowType, :configType, CAST(:content AS jsonb), '1', :active)
                    """,
          new MapSqlParameterSource()
              .addValue("id", id)
              .addValue("tenantId", tenantId)
              .addValue("workflowType", workflowType)
              .addValue("configType", configType.name())
              .addValue("content", json)
              .addValue("active", active));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
