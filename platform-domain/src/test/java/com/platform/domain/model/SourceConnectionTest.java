package com.platform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * SourceConnection's compact constructor enforces that connectionType and config agree on which
 * ConnectionConfig variant is present — a mismatch would mean an ingestion adapter reads the wrong
 * shape of config at runtime. Previously untested.
 */
class SourceConnectionTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Test
  void kafkaConnectionTypeAcceptsKafkaConfig() {
    var connection =
        new SourceConnection(
            "conn-1",
            "kafka-conn",
            "Kafka Connection",
            ConnectionType.KAFKA,
            new ConnectionConfig.KafkaConfig("localhost:9092", "work-items"),
            "vault://creds/conn-1",
            "user-1",
            NOW,
            NOW);

    assertThat(connection.connectionType()).isEqualTo(ConnectionType.KAFKA);
  }

  @Test
  void dbPollConnectionTypeAcceptsDbPollConfig() {
    var connection =
        new SourceConnection(
            "conn-2",
            "db-conn",
            "DB Connection",
            ConnectionType.DB_POLL,
            new ConnectionConfig.DbPollConfig("jdbc:postgresql://localhost/db", "SELECT 1", 30),
            "vault://creds/conn-2",
            "user-1",
            NOW,
            NOW);

    assertThat(connection.connectionType()).isEqualTo(ConnectionType.DB_POLL);
  }

  @Test
  void fileShareConnectionTypeAcceptsFileShareConfig() {
    var connection =
        new SourceConnection(
            "conn-3",
            "file-conn",
            "File Connection",
            ConnectionType.FILE_SHARE,
            new ConnectionConfig.FileShareConfig("/mnt/inbound", "*.csv"),
            "vault://creds/conn-3",
            "user-1",
            NOW,
            NOW);

    assertThat(connection.connectionType()).isEqualTo(ConnectionType.FILE_SHARE);
  }

  @Test
  void kafkaConnectionTypeRejectsDbPollConfig() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SourceConnection(
                    "conn-4",
                    "mismatched",
                    "Mismatched",
                    ConnectionType.KAFKA,
                    new ConnectionConfig.DbPollConfig(
                        "jdbc:postgresql://localhost/db", "SELECT 1", 30),
                    "vault://creds/conn-4",
                    "user-1",
                    NOW,
                    NOW))
        .withMessageContaining("KAFKA");
  }

  @Test
  void dbPollConnectionTypeRejectsFileShareConfig() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SourceConnection(
                    "conn-5",
                    "mismatched",
                    "Mismatched",
                    ConnectionType.DB_POLL,
                    new ConnectionConfig.FileShareConfig("/mnt/inbound", "*.csv"),
                    "vault://creds/conn-5",
                    "user-1",
                    NOW,
                    NOW))
        .withMessageContaining("DB_POLL");
  }

  @Test
  void fileShareConnectionTypeRejectsKafkaConfig() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SourceConnection(
                    "conn-6",
                    "mismatched",
                    "Mismatched",
                    ConnectionType.FILE_SHARE,
                    new ConnectionConfig.KafkaConfig("localhost:9092", "work-items"),
                    "vault://creds/conn-6",
                    "user-1",
                    NOW,
                    NOW))
        .withMessageContaining("FILE_SHARE");
  }

  @Test
  void nullConnectionTypeOrConfigSkipsTypeMatchValidation() {
    // A connection under construction (e.g. mid-wizard draft) may not have both set yet —
    // the compact constructor only validates once both are present.
    var connection =
        new SourceConnection(
            "conn-7", "draft-conn", "Draft Connection", null, null, null, "user-1", NOW, NOW);

    assertThat(connection.connectionType()).isNull();
    assertThat(connection.config()).isNull();
  }
}
