package com.platform.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * ConnectionConfig's three record variants enforce real invariants in their compact constructors,
 * including — for DbPollConfig — a security-relevant rejection of inline JDBC credentials
 * (financial-services requirement: connection secrets must go through credentialsRef, never be
 * embedded in a URL that could end up in logs or config documents). None of it had test coverage.
 */
class ConnectionConfigTest {

    @Test
    void kafkaConfigAcceptsValidValues() {
        var config = new ConnectionConfig.KafkaConfig("localhost:9092", "work-items");

        assertThat(config.bootstrapServers()).isEqualTo("localhost:9092");
        assertThat(config.topicName()).isEqualTo("work-items");
    }

    @Test
    void kafkaConfigRejectsBlankBootstrapServers() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectionConfig.KafkaConfig("  ", "work-items"))
                .withMessageContaining("bootstrapServers");
    }

    @Test
    void kafkaConfigRejectsNullTopicName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectionConfig.KafkaConfig("localhost:9092", null))
                .withMessageContaining("topicName");
    }

    @Test
    void dbPollConfigAcceptsValidValues() {
        var config = new ConnectionConfig.DbPollConfig(
                "jdbc:postgresql://localhost:5432/workflow", "SELECT * FROM work_items", 30);

        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/workflow");
        assertThat(config.pollIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void dbPollConfigRejectsBlankJdbcUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectionConfig.DbPollConfig("", "SELECT 1", 30))
                .withMessageContaining("jdbcUrl");
    }

    @Test
    void dbPollConfigRejectsBlankQuery() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectionConfig.DbPollConfig(
                        "jdbc:postgresql://localhost/db", " ", 30))
                .withMessageContaining("query");
    }

    @Test
    void dbPollConfigRejectsNonPositivePollInterval() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectionConfig.DbPollConfig(
                        "jdbc:postgresql://localhost/db", "SELECT 1", 0))
                .withMessageContaining("pollIntervalSeconds");
    }

    @Test
    void dbPollConfigRejectsInlineUserPasswordCredentials() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectionConfig.DbPollConfig(
                        "jdbc:postgresql://appuser:secret@localhost:5432/workflow", "SELECT 1", 30))
                .withMessageContaining("inline credentials");
    }

    @Test
    void dbPollConfigRejectsPasswordQueryParameter() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectionConfig.DbPollConfig(
                        "jdbc:postgresql://localhost:5432/workflow?password=secret", "SELECT 1", 30))
                .withMessageContaining("inline credentials");
    }

    @Test
    void dbPollConfigRejectsUserQueryParameterCaseInsensitively() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectionConfig.DbPollConfig(
                        "jdbc:postgresql://localhost:5432/workflow?USER=appuser", "SELECT 1", 30))
                .withMessageContaining("inline credentials");
    }

    @Test
    void fileShareConfigAcceptsValidValues() {
        var config = new ConnectionConfig.FileShareConfig("/mnt/inbound", "*.csv");

        assertThat(config.path()).isEqualTo("/mnt/inbound");
        assertThat(config.filePattern()).isEqualTo("*.csv");
    }

    @Test
    void fileShareConfigRejectsBlankPath() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectionConfig.FileShareConfig(" ", "*.csv"))
                .withMessageContaining("path");
    }

    @Test
    void fileShareConfigRejectsBlankFilePattern() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConnectionConfig.FileShareConfig("/mnt/inbound", ""))
                .withMessageContaining("filePattern");
    }
}
