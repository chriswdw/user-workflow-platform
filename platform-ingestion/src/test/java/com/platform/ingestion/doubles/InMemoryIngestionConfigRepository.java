package com.platform.ingestion.doubles;

import com.platform.domain.model.SourceType;
import com.platform.ingestion.domain.model.IngestionConfig;
import com.platform.ingestion.domain.ports.out.IIngestionConfigRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryIngestionConfigRepository implements IIngestionConfigRepository {

    private final Map<String, IngestionConfig> store = new HashMap<>();

    public void save(IngestionConfig config) {
        store.put(key(config.tenantId(), config.workflowType(), config.sourceType()), config);
    }

    @Override
    public Optional<IngestionConfig> findByTenantAndWorkflowTypeAndSourceType(
            String tenantId, String workflowType, SourceType sourceType) {
        return Optional.ofNullable(store.get(key(tenantId, workflowType, sourceType)));
    }

    private static String key(String tenantId, String workflowType, SourceType sourceType) {
        return tenantId + ":" + workflowType + ":" + sourceType;
    }
}
