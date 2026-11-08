package com.platform.domain.model;

import java.time.OffsetDateTime;

public record SourceConnection(
    String id,
    String name,
    String displayName,
    ConnectionType connectionType,
    ConnectionConfig config,
    String credentialsRef,
    int version,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  /** The version a connection has when first stored; every successful update adds one. */
  public static final int INITIAL_VERSION = 1;

  public SourceConnection {
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1, was " + version);
    }
    if (connectionType != null && config != null) {
      boolean typeMatch =
          switch (connectionType) {
            case KAFKA -> config instanceof ConnectionConfig.KafkaConfig;
            case DB_POLL -> config instanceof ConnectionConfig.DbPollConfig;
            case FILE_SHARE -> config instanceof ConnectionConfig.FileShareConfig;
          };
      if (!typeMatch)
        throw new IllegalArgumentException(
            "config type does not match connectionType " + connectionType);
    }
  }
}
