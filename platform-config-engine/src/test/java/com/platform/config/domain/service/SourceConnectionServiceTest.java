package com.platform.config.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.config.doubles.InMemorySourceConnectionRepository;
import com.platform.domain.model.ConnectionConfig;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SourceConnectionService is the admin-facing use case for managing shared source connections
 * (create/update/delete, and per-tenant access grants) — driven directly via its input ports with
 * an in-memory repository double, no Spring context. Previously had zero test coverage.
 */
class SourceConnectionServiceTest {

  private final InMemorySourceConnectionRepository repo = new InMemorySourceConnectionRepository();
  private final SourceConnectionService service = new SourceConnectionService(repo);

  private static SourceConnection draftConnection(String name) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return new SourceConnection(
        "ignored-id",
        name,
        "Display " + name,
        ConnectionType.KAFKA,
        new ConnectionConfig.KafkaConfig("localhost:9092", "topic-" + name),
        "vault://creds",
        "admin-1",
        now,
        now);
  }

  @Test
  void createAssignsAGeneratedIdAndPersists() {
    SourceConnection created = service.create(draftConnection("kafka-a"));

    assertThat(created.id()).isNotBlank().isNotEqualTo("ignored-id");
    assertThat(created.name()).isEqualTo("kafka-a");
    assertThat(repo.findById(created.id())).contains(created);
  }

  @Test
  void createSetsCreatedAtAndUpdatedAtToTheSameInstant() {
    SourceConnection created = service.create(draftConnection("kafka-b"));

    assertThat(created.createdAt()).isEqualTo(created.updatedAt());
  }

  @Test
  void updateMergesProvidedFieldsOverExisting() {
    SourceConnection created = service.create(draftConnection("kafka-c"));

    SourceConnection partialUpdate =
        new SourceConnection(
            created.id(), "kafka-c-renamed", null, null, null, null, null, null, null);
    SourceConnection updated = service.update(partialUpdate);

    assertThat(updated.name()).isEqualTo("kafka-c-renamed");
    // fields not provided in the partial update fall back to the existing values
    assertThat(updated.displayName()).isEqualTo(created.displayName());
    assertThat(updated.connectionType()).isEqualTo(created.connectionType());
    assertThat(updated.config()).isEqualTo(created.config());
    assertThat(updated.credentialsRef()).isEqualTo(created.credentialsRef());
    assertThat(updated.createdBy()).isEqualTo(created.createdBy());
    assertThat(updated.createdAt()).isEqualTo(created.createdAt());
  }

  @Test
  void updateAppliesEveryProvidedFieldOverExisting() {
    SourceConnection created = service.create(draftConnection("kafka-c2"));

    SourceConnection fullUpdate =
        new SourceConnection(
            created.id(),
            "kafka-c2-renamed",
            "New Display Name",
            ConnectionType.DB_POLL,
            new ConnectionConfig.DbPollConfig("jdbc:postgresql://host:5432/db", "SELECT 1", 30),
            "vault://new-creds",
            null,
            null,
            null);

    SourceConnection updated = service.update(fullUpdate);

    assertThat(updated.name()).isEqualTo("kafka-c2-renamed");
    assertThat(updated.displayName()).isEqualTo("New Display Name");
    assertThat(updated.connectionType()).isEqualTo(ConnectionType.DB_POLL);
    assertThat(updated.config()).isEqualTo(fullUpdate.config());
    assertThat(updated.credentialsRef()).isEqualTo("vault://new-creds");
    // createdBy/createdAt are never taken from the incoming request, even when non-null
    assertThat(updated.createdBy()).isEqualTo(created.createdBy());
    assertThat(updated.createdAt()).isEqualTo(created.createdAt());
  }

  @Test
  void updateAdvancesUpdatedAtButPreservesCreatedAt() {
    SourceConnection created = service.create(draftConnection("kafka-d"));

    SourceConnection updated =
        service.update(
            new SourceConnection(
                created.id(), "kafka-d-v2", null, null, null, null, null, null, null));

    assertThat(updated.createdAt()).isEqualTo(created.createdAt());
    assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
  }

  @Test
  void updateThrowsWhenConnectionDoesNotExist() {
    SourceConnection nonExistent =
        new SourceConnection("missing-id", "x", null, null, null, null, null, null, null);

    assertThat(catchThrowableMessage(() -> service.update(nonExistent))).contains("missing-id");
  }

  @Test
  void deleteRemovesTheConnection() {
    SourceConnection created = service.create(draftConnection("kafka-e"));

    service.delete(created.id());

    assertThat(repo.findById(created.id())).isEmpty();
  }

  @Test
  void grantAccessMakesConnectionVisibleToTenantByType() {
    SourceConnection created = service.create(draftConnection("kafka-f"));

    service.grantAccess(created.id(), "tenant-1", "admin-1");

    List<SourceConnection> accessible = service.listAccessible("tenant-1", ConnectionType.KAFKA);
    assertThat(accessible).extracting(SourceConnection::id).containsExactly(created.id());
  }

  @Test
  void listAccessibleExcludesConnectionsNotGrantedToTheTenant() {
    service.create(draftConnection("kafka-g"));

    assertThat(service.listAccessible("tenant-never-granted", ConnectionType.KAFKA)).isEmpty();
  }

  @Test
  void revokeAccessRemovesConnectionFromTenantsAccessibleList() {
    SourceConnection created = service.create(draftConnection("kafka-h"));
    service.grantAccess(created.id(), "tenant-1", "admin-1");

    service.revokeAccess(created.id(), "tenant-1");

    assertThat(service.listAccessible("tenant-1", ConnectionType.KAFKA)).isEmpty();
  }

  @Test
  void listAllReturnsEveryConnectionRegardlessOfAccessGrants() {
    service.create(draftConnection("kafka-i"));
    service.create(draftConnection("kafka-j"));

    assertThat(service.listAll()).hasSize(2);
  }

  private static String catchThrowableMessage(Runnable action) {
    try {
      action.run();
      return null;
    } catch (RuntimeException e) {
      return e.getMessage();
    }
  }
}
