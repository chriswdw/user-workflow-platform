package com.platform.config.domain.model;

import java.util.Map;

/**
 * A stored configuration document.
 * content is the raw parsed JSON/YAML payload — consumers extract typed fields via key lookup.
 * workflowType may be null for tenant-scoped configs (e.g. RESOLUTION_GROUP, USER_ROLE_CONFIG).
 */
public record ConfigDocument(
        String id,
        String tenantId,
        String workflowType,
        ConfigType configType,
        Map<String, Object> content,
        String version,
        boolean active
) {}
