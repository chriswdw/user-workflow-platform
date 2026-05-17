package com.platform.domain.model;

import java.time.OffsetDateTime;

public record SourceConnection(
        String id,
        String name,
        String displayName,
        ConnectionType connectionType,
        ConnectionConfig config,
        String credentialsRef,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public SourceConnection {
        if (connectionType != null && config != null) {
            boolean typeMatch = switch (connectionType) {
                case KAFKA      -> config instanceof ConnectionConfig.KafkaConfig;
                case DB_POLL    -> config instanceof ConnectionConfig.DbPollConfig;
                case FILE_SHARE -> config instanceof ConnectionConfig.FileShareConfig;
            };
            if (!typeMatch)
                throw new IllegalArgumentException(
                        "config type does not match connectionType " + connectionType);
        }
    }
}
