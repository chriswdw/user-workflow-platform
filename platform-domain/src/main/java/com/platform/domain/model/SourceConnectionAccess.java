package com.platform.domain.model;

import java.time.OffsetDateTime;

public record SourceConnectionAccess(
        String id,
        String sourceConnectionId,
        String tenantId,
        String grantedBy,
        OffsetDateTime grantedAt
) {}
