package com.platform.workflow.doubles;

import com.platform.workflow.domain.model.WorkflowConfig;
import com.platform.workflow.domain.ports.out.IWorkflowConfigRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryWorkflowConfigRepository implements IWorkflowConfigRepository {

    private final Map<String, WorkflowConfig> store = new HashMap<>();

    @Override
    public Optional<WorkflowConfig> findActiveByTenantAndWorkflowType(String tenantId, String workflowType) {
        return Optional.ofNullable(store.get(key(tenantId, workflowType)));
    }

    public void save(WorkflowConfig config) {
        store.put(key(config.tenantId(), config.workflowType()), config);
    }

    private static String key(String tenantId, String workflowType) {
        return tenantId + ":" + workflowType;
    }
}
