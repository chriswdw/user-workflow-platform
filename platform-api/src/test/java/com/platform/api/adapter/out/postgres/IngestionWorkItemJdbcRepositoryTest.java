package com.platform.api.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.domain.model.SourceType;
import com.platform.domain.model.WorkItem;
import com.platform.ingestion.domain.exception.DuplicateIdempotencyKeyException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class IngestionWorkItemJdbcRepositoryTest {

  private static final NamedParameterJdbcTemplate jdbc =
      new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
  private static final ObjectMapper objectMapper = new JsonMapper();

  private final IngestionWorkItemJdbcRepository repository =
      new IngestionWorkItemJdbcRepository(jdbc, objectMapper);
  private final WorkItemJdbcRepository queryRepository =
      new WorkItemJdbcRepository(jdbc, objectMapper);

  @BeforeEach
  void truncate() {
    jdbc.update("TRUNCATE work_items CASCADE", Map.of());
  }

  @Test
  void save_insertsNewWorkItemAndReturnsIt() {
    WorkItem item = workItem("wi-ing-1", "tenant-1", "SETTLEMENT_EXCEPTION");

    WorkItem saved = repository.save(item);

    assertThat(saved.id()).isEqualTo("wi-ing-1");
    assertThat(saved.tenantId()).isEqualTo("tenant-1");
    assertThat(saved.status()).isEqualTo("UNDER_REVIEW");
    assertThat(saved.source()).isEqualTo(SourceType.KAFKA);
    assertThat(saved.idempotencyKey()).isEqualTo("TRD-ING-001");
  }

  @Test
  void save_persistsFieldsAsJsonb() {
    WorkItem item = workItem("wi-ing-2", "tenant-1", "SETTLEMENT_EXCEPTION");

    repository.save(item);

    WorkItem fromDb = queryRepository.findById("tenant-1", "wi-ing-2").orElseThrow();
    assertThat(fromDb.fields()).containsEntry("tradeRef", "TRD-ING-001");
  }

  @Test
  void save_throwsDuplicateIdempotencyKeyExceptionOnConstraintViolation() {
    repository.save(workItem("wi-ing-dup-1", "tenant-1", "SETTLEMENT_EXCEPTION"));

    // Same idempotency key, different work item id — simulates concurrent duplicate
    WorkItem concurrent =
        new WorkItem(
            "wi-ing-dup-2",
            "tenant-1",
            "SETTLEMENT_EXCEPTION",
            "corr-2",
            null,
            SourceType.KAFKA,
            "src-2",
            "TRD-ING-001",
            "UNDER_REVIEW",
            "group-ops",
            false,
            Map.of(),
            null,
            null,
            null,
            null,
            null,
            1,
            "system",
            Instant.now(),
            Instant.now());

    assertThatThrownBy(() -> repository.save(concurrent))
        .isInstanceOf(DuplicateIdempotencyKeyException.class)
        .hasMessageContaining("TRD-ING-001");
  }

  // ── Only the idempotency-key index means "duplicate" ─────────────────────

  @Test
  void save_doesNotMislabelANotNullViolationAsADuplicate() {
    WorkItem unassigned = workItemWith("wi-ing-null", "TRD-ING-NULL", null);

    assertThatThrownBy(() -> repository.save(unassigned))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateKeyException.class)
        .isNotInstanceOf(DuplicateIdempotencyKeyException.class);
  }

  @Test
  void save_doesNotMislabelAPrimaryKeyCollisionAsADuplicateIdempotencyKey() {
    repository.save(workItemWith("wi-ing-pk", "TRD-ING-PK-1", "group-ops"));

    // Same work item id, a different idempotency key: a genuine bug, not a re-delivered record.
    WorkItem sameIdDifferentKey = workItemWith("wi-ing-pk", "TRD-ING-PK-2", "group-ops");

    assertThatThrownBy(() -> repository.save(sameIdDifferentKey))
        .isInstanceOf(DuplicateKeyException.class)
        .isNotInstanceOf(DuplicateIdempotencyKeyException.class);
  }

  @Test
  void save_surfacesAUniqueViolationWhoseConstraintCannotBeDetermined() {
    // If the driver gives no constraint detail we cannot know it was the idempotency index, so the
    // violation must surface as it is, never be assumed to be a duplicate delivery.
    var unidentifiable =
        new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE) {
          @Override
          public int update(String sql, SqlParameterSource paramSource) {
            throw new DuplicateKeyException("unique violation with no driver detail");
          }
        };
    var repo = new IngestionWorkItemJdbcRepository(unidentifiable, objectMapper);
    WorkItem item = workItemWith("wi-ing-unknown", "TRD-ING-UNKNOWN", "group-ops");

    assertThatThrownBy(() -> repo.save(item))
        .isInstanceOf(DuplicateKeyException.class)
        .isNotInstanceOf(DuplicateIdempotencyKeyException.class);
  }

  @Test
  void save_respectsTenantIsolation() {
    repository.save(workItem("wi-ing-3", "tenant-A", "SETTLEMENT_EXCEPTION"));

    assertThat(queryRepository.findById("tenant-B", "wi-ing-3")).isEmpty();
    assertThat(queryRepository.findById("tenant-A", "wi-ing-3")).isPresent();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private static WorkItem workItemWith(String id, String idempotencyKey, String assignedGroup) {
    Instant now = Instant.now();
    return new WorkItem(
        id,
        "tenant-1",
        "SETTLEMENT_EXCEPTION",
        "corr-" + id,
        null,
        SourceType.KAFKA,
        "src-" + id,
        idempotencyKey,
        "UNDER_REVIEW",
        assignedGroup,
        false,
        Map.of(),
        null,
        null,
        null,
        null,
        null,
        1,
        "system",
        now,
        now);
  }

  private static WorkItem workItem(String id, String tenantId, String workflowType) {
    Instant now = Instant.now();
    return new WorkItem(
        id,
        tenantId,
        workflowType,
        "corr-" + id,
        null,
        SourceType.KAFKA,
        "src-" + id,
        "TRD-ING-001",
        "UNDER_REVIEW",
        "group-ops",
        false,
        Map.of("tradeRef", "TRD-ING-001"),
        null,
        null,
        null,
        null,
        null,
        1,
        "system",
        now,
        now);
  }
}
