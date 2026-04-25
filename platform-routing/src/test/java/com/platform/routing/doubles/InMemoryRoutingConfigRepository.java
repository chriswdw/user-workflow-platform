package com.platform.routing.doubles;

import com.platform.routing.domain.model.RoutingConfig;
import com.platform.routing.domain.ports.out.IRoutingConfigRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory test double for IRoutingConfigRepository.
 * Used by Cucumber step definitions to seed routing config without a database.
 */
public class InMemoryRoutingConfigRepository implements IRoutingConfigRepository {

    private final Map<String, RoutingConfig> store = new HashMap<>();

    public void save(RoutingConfig config) {
        store.put(key(config.tenantId(), config.workflowType()), config);
    }

    @Override
    public Optional<RoutingConfig> findActiveByTenantAndWorkflowType(String tenantId, String workflowType) {
        return Optional.ofNullable(store.get(key(tenantId, workflowType)));
    }

    private static String key(String tenantId, String workflowType) {
        return tenantId + ":" + workflowType;
    }
}
