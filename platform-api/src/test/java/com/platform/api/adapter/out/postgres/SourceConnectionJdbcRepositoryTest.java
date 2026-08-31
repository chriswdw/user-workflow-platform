package com.platform.api.adapter.out.postgres;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.platform.domain.model.ConnectionConfig;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import com.platform.domain.model.SourceConnectionAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceConnectionJdbcRepositoryTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new JsonMapper();

    private final SourceConnectionJdbcRepository repository =
            new SourceConnectionJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE source_connections CASCADE", Map.of());
    }

    @Test
    void save_kafkaConnection_roundtripsAllFields() {
        SourceConnection conn = kafkaConnection("conn-1", "kafka-primary", "Primary Kafka", "ref-123");

        repository.save(conn);
        Optional<SourceConnection> found = repository.findById("conn-1");

        assertThat(found).isPresent();
        SourceConnection result = found.get();
        assertThat(result.id()).isEqualTo("conn-1");
        assertThat(result.name()).isEqualTo("kafka-primary");
        assertThat(result.displayName()).isEqualTo("Primary Kafka");
        assertThat(result.connectionType()).isEqualTo(ConnectionType.KAFKA);
        assertThat(result.config()).isInstanceOf(ConnectionConfig.KafkaConfig.class);
        ConnectionConfig.KafkaConfig config = (ConnectionConfig.KafkaConfig) result.config();
        assertThat(config.bootstrapServers()).isEqualTo("localhost:9092");
        assertThat(config.topicName()).isEqualTo("test-topic");
        assertThat(result.credentialsRef()).isEqualTo("ref-123");
    }

    @Test
    void save_dbPollConnection_roundtripsAllFields() {
        SourceConnection conn = dbPollConnection("conn-7", "db-poll", "DB Poll", null);

        repository.save(conn);
        Optional<SourceConnection> found = repository.findById("conn-7");

        assertThat(found).isPresent();
        SourceConnection result = found.get();
        assertThat(result.connectionType()).isEqualTo(ConnectionType.DB_POLL);
        assertThat(result.config()).isInstanceOf(ConnectionConfig.DbPollConfig.class);
        ConnectionConfig.DbPollConfig config = (ConnectionConfig.DbPollConfig) result.config();
        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/mydb");
        assertThat(config.query()).isEqualTo("SELECT * FROM items WHERE processed = false");
        assertThat(config.pollIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void save_fileShareConnection_roundtripsAllFields() {
        SourceConnection conn = fileShareConnection("conn-8", "file-share", "File Share", null);

        repository.save(conn);
        Optional<SourceConnection> found = repository.findById("conn-8");

        assertThat(found).isPresent();
        SourceConnection result = found.get();
        assertThat(result.connectionType()).isEqualTo(ConnectionType.FILE_SHARE);
        assertThat(result.config()).isInstanceOf(ConnectionConfig.FileShareConfig.class);
        ConnectionConfig.FileShareConfig config = (ConnectionConfig.FileShareConfig) result.config();
        assertThat(config.path()).isEqualTo("/data/feeds");
        assertThat(config.filePattern()).isEqualTo("*.csv");
    }

    @Test
    void grantAccess_hasAccess_returnsTrue() {
        repository.save(dbPollConnection("conn-2", "db-primary", "Primary DB", null));

        repository.grantAccess(access("acc-1", "conn-2", "tenant-A"));

        assertThat(repository.hasAccess("conn-2", "tenant-A")).isTrue();
        assertThat(repository.hasAccess("conn-2", "tenant-B")).isFalse();
    }

    @Test
    void revokeAccess_hasAccess_returnsFalse() {
        repository.save(fileShareConnection("conn-3", "file-primary", "Primary File Share", null));
        repository.grantAccess(access("acc-2", "conn-3", "tenant-A"));
        assertThat(repository.hasAccess("conn-3", "tenant-A")).isTrue();

        repository.revokeAccess("conn-3", "tenant-A");

        assertThat(repository.hasAccess("conn-3", "tenant-A")).isFalse();
    }

    @Test
    void findAccessibleByTenantAndType_returnsOnlyGrantedAndMatchingType() {
        repository.save(kafkaConnection("conn-4", "kafka-a", "Kafka A", null));
        repository.save(dbPollConnection("conn-5", "db-a", "DB A", null));
        repository.grantAccess(access("acc-3", "conn-4", "tenant-A"));
        repository.grantAccess(access("acc-4", "conn-5", "tenant-A"));

        List<SourceConnection> result = repository.findAccessibleByTenantAndType("tenant-A", ConnectionType.KAFKA);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("conn-4");
    }

    @Test
    void findAccessibleByTenantAndType_excludesConnectionsNotGrantedToTenant() {
        repository.save(kafkaConnection("conn-6", "kafka-b", "Kafka B", null));
        repository.grantAccess(access("acc-5", "conn-6", "tenant-B"));

        List<SourceConnection> result = repository.findAccessibleByTenantAndType("tenant-A", ConnectionType.KAFKA);

        assertThat(result).isEmpty();
    }

    @Test
    void findById_throwsIllegalStateWhenConfigIsNotAJsonObject() {
        // config is valid JSONB but not a JSON object, so Jackson's Map<String,Object>
        // deserialisation fails — simulates data corruption upstream of this adapter.
        repository.save(kafkaConnection("conn-corrupt", "kafka-corrupt", "Kafka Corrupt", null));
        jdbc.update("UPDATE source_connections SET config = CAST(:config AS jsonb) WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("config", "[\"not\", \"an\", \"object\"]")
                        .addValue("id", "conn-corrupt"));

        assertThatThrownBy(() -> repository.findById("conn-corrupt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialise config from JSONB");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static SourceConnection kafkaConnection(String id, String name, String displayName,
                                                    String credentialsRef) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new SourceConnection(id, name, displayName, ConnectionType.KAFKA,
                new ConnectionConfig.KafkaConfig("localhost:9092", "test-topic"),
                credentialsRef, "admin", now, now);
    }

    private static SourceConnection dbPollConnection(String id, String name, String displayName,
                                                     String credentialsRef) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new SourceConnection(id, name, displayName, ConnectionType.DB_POLL,
                new ConnectionConfig.DbPollConfig(
                        "jdbc:postgresql://localhost:5432/mydb",
                        "SELECT * FROM items WHERE processed = false",
                        30),
                credentialsRef, "admin", now, now);
    }

    private static SourceConnection fileShareConnection(String id, String name, String displayName,
                                                        String credentialsRef) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new SourceConnection(id, name, displayName, ConnectionType.FILE_SHARE,
                new ConnectionConfig.FileShareConfig("/data/feeds", "*.csv"),
                credentialsRef, "admin", now, now);
    }

    private static SourceConnectionAccess access(String id, String connectionId, String tenantId) {
        return new SourceConnectionAccess(id, connectionId, tenantId, "admin",
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
