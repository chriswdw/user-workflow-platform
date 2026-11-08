package com.platform.api.doubles;

import com.platform.api.domain.ports.IFindWorkItemPort;
import com.platform.api.domain.ports.IListWorkItemsPort;
import com.platform.domain.model.WorkItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryWorkItemQueryPort implements IFindWorkItemPort, IListWorkItemsPort {

  private final Map<String, WorkItem> store = new HashMap<>();

  public void save(WorkItem workItem) {
    store.put(key(workItem.tenantId(), workItem.id()), workItem);
  }

  @Override
  public Optional<WorkItem> findById(String tenantId, String workItemId) {
    return Optional.ofNullable(store.get(key(tenantId, workItemId)));
  }

  @Override
  public List<WorkItem> findByTenantAndWorkflowType(String tenantId, String workflowType) {
    return store.values().stream()
        .filter(w -> tenantId.equals(w.tenantId()) && workflowType.equals(w.workflowType()))
        .toList();
  }

  private static String key(String tenantId, String workItemId) {
    return tenantId + ":" + workItemId;
  }
}
