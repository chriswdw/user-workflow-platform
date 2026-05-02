package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import com.platform.domain.model.SourceConnectionAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SourceConnectionJdbcRepositoryTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SourceConnectionJdbcRepository repository =
            new SourceConnectionJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE source_connections CASCADE", Map.of());
    }

    @Test
    void save_newConnection_roundtripsAllFields() {
        SourceConnection conn = connection("conn-1", "kafka-primary", "Primary Kafka",
                ConnectionType.KAFKA, "ref-123");

        repository.save(conn);
        Optional<SourceConnection> found = repository.findById("conn-1");

        assertThat(found).isPresent();
        SourceConnection result = found.get();
        assertThat(result.id()).isEqualTo("conn-1");
        assertThat(result.name()).isEqualTo("kafka-primary");
        assertThat(result.displayName()).isEqualTo("Primary Kafka");
        assertThat(result.connectionType()).isEqualTo(ConnectionType.KAFKA);
        assertThat(result.config()).containsKey("bootstrap.servers");
        assertThat(result.credentialsRef()).isEqualTo("ref-123");
    }

    @Test
    void grantAccess_hasAccess_returnsTrue() {
        repository.save(connection("conn-2", "db-primary", "Primary DB",
                ConnectionType.DB, null));

        repository.grantAccess(access("acc-1", "conn-2", "tenant-A"));

        assertThat(repository.hasAccess("conn-2", "tenant-A")).isTrue();
        assertThat(repository.hasAccess("conn-2", "tenant-B")).isFalse();
    }

    @Test
    void revokeAccess_hasAccess_returnsFalse() {
        repository.save(connection("conn-3", "file-primary", "Primary File Share",
                ConnectionType.FILE_SHARE, null));
        repository.grantAccess(access("acc-2", "conn-3", "tenant-A"));
        assertThat(repository.hasAccess("conn-3", "tenant-A")).isTrue();

        repository.revokeAccess("conn-3", "tenant-A");

        assertThat(repository.hasAccess("conn-3", "tenant-A")).isFalse();
    }

    @Test
    void findAccessibleByTenantAndType_returnsOnlyGrantedAndMatchingType() {
        repository.save(connection("conn-4", "kafka-a", "Kafka A", ConnectionType.KAFKA, null));
        repository.save(connection("conn-5", "db-a", "DB A", ConnectionType.DB, null));
        repository.grantAccess(access("acc-3", "conn-4", "tenant-A"));
        repository.grantAccess(access("acc-4", "conn-5", "tenant-A"));

        List<SourceConnection> result = repository.findAccessibleByTenantAndType("tenant-A", ConnectionType.KAFKA);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("conn-4");
    }

    @Test
    void findAccessibleByTenantAndType_excludesConnectionsNotGrantedToTenant() {
        repository.save(connection("conn-6", "kafka-b", "Kafka B", ConnectionType.KAFKA, null));
        repository.grantAccess(access("acc-5", "conn-6", "tenant-B"));

        List<SourceConnection> result = repository.findAccessibleByTenantAndType("tenant-A", ConnectionType.KAFKA);

        assertThat(result).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static SourceConnection connection(String id, String name, String displayName,
                                               ConnectionType type, String credentialsRef) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new SourceConnection(
                id, name, displayName, type,
                Map.of("bootstrap.servers", "localhost:9092"),
                credentialsRef, "admin", now, now);
    }

    private static SourceConnectionAccess access(String id, String connectionId, String tenantId) {
        return new SourceConnectionAccess(id, connectionId, tenantId, "admin",
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
