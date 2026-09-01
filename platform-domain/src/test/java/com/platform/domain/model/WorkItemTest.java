package com.platform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * WorkItem is the central domain entity; every state change goes through one of its "with" methods,
 * which must return a new immutable instance with exactly one field changed (plus updatedAt
 * advancing) and every other field carried over unchanged. This invariant underpins the
 * optimistic-locking and audit-log guarantees the platform relies on, and previously had zero
 * direct test coverage.
 */
class WorkItemTest {

  private static WorkItem baseWorkItem() {
    Instant created = Instant.parse("2026-01-01T00:00:00Z");
    return new WorkItem(
        "wi-1",
        "tenant-1",
        "TRADE_BREAK",
        "corr-1",
        "cfg-v1",
        SourceType.KAFKA,
        "topic-ref",
        "idem-1",
        "OPEN",
        "group-A",
        true,
        Map.of("trade", Map.of("ref", "TRD-001")),
        50,
        "MEDIUM",
        created,
        null,
        null,
        1,
        "maker-1",
        created,
        created);
  }

  @Test
  void withStatusChangesOnlyStatusAndUpdatedAt() {
    WorkItem original = baseWorkItem();

    WorkItem updated = original.withStatus("RESOLVED");

    assertThat(updated.status()).isEqualTo("RESOLVED");
    assertThat(updated.id()).isEqualTo(original.id());
    assertThat(updated.assignedGroup()).isEqualTo(original.assignedGroup());
    assertThat(updated.fields()).isEqualTo(original.fields());
    assertThat(updated.version()).isEqualTo(original.version());
    assertThat(updated.updatedAt()).isAfterOrEqualTo(original.updatedAt());
    // original instance is untouched — WorkItem is immutable
    assertThat(original.status()).isEqualTo("OPEN");
  }

  @Test
  void withAssignedGroupChangesOnlyGroupAndUpdatedAt() {
    WorkItem original = baseWorkItem();

    WorkItem updated = original.withAssignedGroup("group-B");

    assertThat(updated.assignedGroup()).isEqualTo("group-B");
    assertThat(updated.status()).isEqualTo(original.status());
    assertThat(original.assignedGroup()).isEqualTo("group-A");
  }

  @Test
  void withMakerUserIdChangesOnlyMakerUserIdAndUpdatedAt() {
    WorkItem original = baseWorkItem();

    WorkItem updated = original.withMakerUserId("maker-2");

    assertThat(updated.makerUserId()).isEqualTo("maker-2");
    assertThat(updated.status()).isEqualTo(original.status());
    assertThat(original.makerUserId()).isEqualTo("maker-1");
  }

  @Test
  void withFieldsChangesOnlyFieldsAndUpdatedAt() {
    WorkItem original = baseWorkItem();
    Map<String, Object> newFields = Map.of("trade", Map.of("ref", "TRD-002"));

    WorkItem updated = original.withFields(newFields);

    assertThat(updated.fields()).isEqualTo(newFields);
    assertThat(updated.status()).isEqualTo(original.status());
    assertThat(original.fields()).isEqualTo(Map.of("trade", Map.of("ref", "TRD-001")));
  }

  @Test
  void withPendingMakerCheckerSetsBothCheckerFieldsAndUpdatedAt() {
    WorkItem original = baseWorkItem();

    WorkItem updated = original.withPendingMakerChecker("checker-1", "APPROVE");

    assertThat(updated.pendingCheckerId()).isEqualTo("checker-1");
    assertThat(updated.pendingCheckerTransition()).isEqualTo("APPROVE");
    assertThat(updated.status()).isEqualTo(original.status());
    assertThat(original.pendingCheckerId()).isNull();
    assertThat(original.pendingCheckerTransition()).isNull();
  }

  @Test
  void clearPendingMakerCheckerNullsBothCheckerFields() {
    WorkItem withPending = baseWorkItem().withPendingMakerChecker("checker-1", "APPROVE");

    WorkItem cleared = withPending.clearPendingMakerChecker();

    assertThat(cleared.pendingCheckerId()).isNull();
    assertThat(cleared.pendingCheckerTransition()).isNull();
    assertThat(cleared.status()).isEqualTo(withPending.status());
    // the intermediate instance is untouched
    assertThat(withPending.pendingCheckerId()).isEqualTo("checker-1");
  }
}
