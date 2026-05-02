package com.platform.config.domain.model;

import java.util.Map;

public record DraftConfigs(
        Map<String, Object> workflowTypeDefinition,
        Map<String, Object> fieldTypeRegistry,
        Map<String, Object> ingestionSourceConfig,
        Map<String, Object> workflowConfig,
        Map<String, Object> blotterConfig,
        Map<String, Object> detailViewConfig
) {
    public boolean isComplete() {
        return blotterConfig != null && !blotterConfig.isEmpty()
                && detailViewConfig != null && !detailViewConfig.isEmpty();
    }
}
