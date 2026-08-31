package com.platform.api.adapter.in.rest;

import com.platform.domain.model.ConnectionConfig;
import com.platform.domain.model.ConnectionType;

import java.util.Map;

public record SourceConnectionRequest(
        String name,
        String displayName,
        String connectionType,
        Map<String, Object> config,
        String credentialsRef
) {
    public ConnectionType parsedConnectionType() {
        return connectionType != null ? ConnectionType.valueOf(connectionType) : null;
    }

    public ConnectionConfig toConnectionConfig() {
        if (connectionType == null || config == null) return null;
        return switch (ConnectionType.valueOf(connectionType)) {
            case KAFKA -> new ConnectionConfig.KafkaConfig(
                    stringOrNull(config, "bootstrapServers"),
                    stringOrNull(config, "topicName"));
            case DB_POLL -> new ConnectionConfig.DbPollConfig(
                    stringOrNull(config, "jdbcUrl"),
                    stringOrNull(config, "query"),
                    config.containsKey("pollIntervalSeconds")
                            ? ((Number) config.get("pollIntervalSeconds")).intValue()
                            : 60);
            case FILE_SHARE -> new ConnectionConfig.FileShareConfig(
                    stringOrNull(config, "path"),
                    stringOrNull(config, "filePattern"));
        };
    }

    private static String stringOrNull(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }
}
