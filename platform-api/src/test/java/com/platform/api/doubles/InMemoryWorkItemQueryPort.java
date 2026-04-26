package com.platform.api.doubles;

import com.platform.api.domain.ports.IFindWorkItemPort;
import com.platform.domain.model.WorkItem;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryWorkItemQueryPort implements IFindWorkItemPort {

    private final Map<String, WorkItem> store = new HashMap<>();

    public void save(WorkItem workItem) {
        store.put(key(workItem.tenantId(), workItem.id()), workItem);
    }

    @Override
    public Optional<WorkItem> findById(String tenantId, String workItemId) {
        return Optional.ofNullable(store.get(key(tenantId, workItemId)));
    }

    private static String key(String tenantId, String workItemId) {
        return tenantId + ":" + workItemId;
    }
}
