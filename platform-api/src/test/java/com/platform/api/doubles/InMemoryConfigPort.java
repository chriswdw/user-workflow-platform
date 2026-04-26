package com.platform.api.doubles;

import com.platform.config.domain.exception.ConfigNotFoundException;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.ports.in.ILoadConfigUseCase;

import java.util.HashMap;
import java.util.Map;

public class InMemoryConfigPort implements ILoadConfigUseCase {

    private final Map<String, ConfigDocument> store = new HashMap<>();

    public void save(ConfigDocument doc) {
        store.put(key(doc.tenantId(), doc.workflowType(), doc.configType()), doc);
    }

    @Override
    public ConfigDocument loadActive(String tenantId, String workflowType, ConfigType configType) {
        ConfigDocument doc = store.get(key(tenantId, workflowType, configType));
        if (doc == null) {
            throw new ConfigNotFoundException(
                    "No active config for tenantId=" + tenantId
                    + ", workflowType=" + workflowType
                    + ", configType=" + configType);
        }
        return doc;
    }

    private static String key(String tenantId, String workflowType, ConfigType configType) {
        return tenantId + ":" + workflowType + ":" + configType;
    }
}
