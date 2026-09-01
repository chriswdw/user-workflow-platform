package com.platform.api.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.domain.model.ConnectionConfig;
import com.platform.domain.model.ConnectionType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SourceConnectionRequest maps the REST payload's untyped connectionType/config strings/map into
 * the correct ConnectionConfig sealed variant. Only the KAFKA branch was exercised by the Cucumber
 * source-connection scenarios; DB_POLL and FILE_SHARE (and the pollIntervalSeconds default) had no
 * coverage.
 */
class SourceConnectionRequestTest {

  @Test
  void parsedConnectionTypeReturnsNullWhenConnectionTypeIsNull() {
    var request = new SourceConnectionRequest(null, null, null, null, null);

    assertThat(request.parsedConnectionType()).isNull();
  }

  @Test
  void parsedConnectionTypeParsesTheEnum() {
    var request = new SourceConnectionRequest("conn", "Conn", "KAFKA", Map.of(), null);

    assertThat(request.parsedConnectionType()).isEqualTo(ConnectionType.KAFKA);
  }

  @Test
  void toConnectionConfigReturnsNullWhenConnectionTypeIsNull() {
    var request = new SourceConnectionRequest("conn", "Conn", null, Map.of(), null);

    assertThat(request.toConnectionConfig()).isNull();
  }

  @Test
  void toConnectionConfigReturnsNullWhenConfigIsNull() {
    var request = new SourceConnectionRequest("conn", "Conn", "KAFKA", null, null);

    assertThat(request.toConnectionConfig()).isNull();
  }

  @Test
  void toConnectionConfigBuildsKafkaConfig() {
    var request =
        new SourceConnectionRequest(
            "conn",
            "Conn",
            "KAFKA",
            Map.of("bootstrapServers", "localhost:9092", "topicName", "work-items"),
            null);

    ConnectionConfig config = request.toConnectionConfig();

    assertThat(config).isInstanceOf(ConnectionConfig.KafkaConfig.class);
    var kafka = (ConnectionConfig.KafkaConfig) config;
    assertThat(kafka.bootstrapServers()).isEqualTo("localhost:9092");
    assertThat(kafka.topicName()).isEqualTo("work-items");
  }

  @Test
  void toConnectionConfigBuildsDbPollConfigWithExplicitPollInterval() {
    var request =
        new SourceConnectionRequest(
            "conn",
            "Conn",
            "DB_POLL",
            Map.of(
                "jdbcUrl",
                "jdbc:postgresql://localhost/db",
                "query",
                "SELECT 1",
                "pollIntervalSeconds",
                45),
            null);

    ConnectionConfig config = request.toConnectionConfig();

    assertThat(config).isInstanceOf(ConnectionConfig.DbPollConfig.class);
    var dbPoll = (ConnectionConfig.DbPollConfig) config;
    assertThat(dbPoll.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost/db");
    assertThat(dbPoll.query()).isEqualTo("SELECT 1");
    assertThat(dbPoll.pollIntervalSeconds()).isEqualTo(45);
  }

  @Test
  void toConnectionConfigBuildsDbPollConfigWithDefaultPollIntervalWhenAbsent() {
    var request =
        new SourceConnectionRequest(
            "conn",
            "Conn",
            "DB_POLL",
            Map.of("jdbcUrl", "jdbc:postgresql://localhost/db", "query", "SELECT 1"),
            null);

    var dbPoll = (ConnectionConfig.DbPollConfig) request.toConnectionConfig();

    assertThat(dbPoll.pollIntervalSeconds()).isEqualTo(60);
  }

  @Test
  void toConnectionConfigBuildsFileShareConfig() {
    var request =
        new SourceConnectionRequest(
            "conn",
            "Conn",
            "FILE_SHARE",
            Map.of("path", "/mnt/inbound", "filePattern", "*.csv"),
            null);

    ConnectionConfig config = request.toConnectionConfig();

    assertThat(config).isInstanceOf(ConnectionConfig.FileShareConfig.class);
    var fileShare = (ConnectionConfig.FileShareConfig) config;
    assertThat(fileShare.path()).isEqualTo("/mnt/inbound");
    assertThat(fileShare.filePattern()).isEqualTo("*.csv");
  }
}
