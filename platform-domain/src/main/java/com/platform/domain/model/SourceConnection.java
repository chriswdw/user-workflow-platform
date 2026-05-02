package com.platform.domain.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record SourceConnection(
        String id,
        String name,
        String displayName,
        ConnectionType connectionType,
        Map<String, Object> config,
        String credentialsRef,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
