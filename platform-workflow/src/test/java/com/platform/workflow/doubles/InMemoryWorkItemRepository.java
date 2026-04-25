package com.platform.workflow.doubles;

import com.platform.domain.model.WorkItem;
import com.platform.workflow.domain.ports.out.IWorkItemRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryWorkItemRepository implements IWorkItemRepository {

    private final Map<String, WorkItem> store = new HashMap<>();

    @Override
    public Optional<WorkItem> findById(String tenantId, String workItemId) {
        return Optional.ofNullable(store.get(key(tenantId, workItemId)));
    }

    @Override
    public WorkItem save(WorkItem workItem) {
        store.put(key(workItem.tenantId(), workItem.id()), workItem);
        return workItem;
    }

    private static String key(String tenantId, String workItemId) {
        return tenantId + ":" + workItemId;
    }
}
