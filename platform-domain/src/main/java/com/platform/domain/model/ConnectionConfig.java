package com.platform.domain.model;

import java.util.regex.Pattern;

public sealed interface ConnectionConfig
        permits ConnectionConfig.KafkaConfig,
                ConnectionConfig.DbPollConfig,
                ConnectionConfig.FileShareConfig {

    record KafkaConfig(String bootstrapServers, String topicName) implements ConnectionConfig {
        public KafkaConfig {
            if (bootstrapServers == null || bootstrapServers.isBlank())
                throw new IllegalArgumentException("bootstrapServers must not be blank");
            if (topicName == null || topicName.isBlank())
                throw new IllegalArgumentException("topicName must not be blank");
        }
    }

    record DbPollConfig(String jdbcUrl, String query, int pollIntervalSeconds) implements ConnectionConfig {
        private static final Pattern CREDENTIAL_PATTERN =
                Pattern.compile("://[^@]*:[^@]+@|[?&](password|user)=", Pattern.CASE_INSENSITIVE);

        public DbPollConfig {
            if (jdbcUrl == null || jdbcUrl.isBlank())
                throw new IllegalArgumentException("jdbcUrl must not be blank");
            if (CREDENTIAL_PATTERN.matcher(jdbcUrl).find())
                throw new IllegalArgumentException("jdbcUrl must not contain inline credentials");
            if (query == null || query.isBlank())
                throw new IllegalArgumentException("query must not be blank");
            if (pollIntervalSeconds <= 0)
                throw new IllegalArgumentException("pollIntervalSeconds must be positive");
        }
    }

    record FileShareConfig(String path, String filePattern) implements ConnectionConfig {
        public FileShareConfig {
            if (path == null || path.isBlank())
                throw new IllegalArgumentException("path must not be blank");
            if (filePattern == null || filePattern.isBlank())
                throw new IllegalArgumentException("filePattern must not be blank");
        }
    }
}
