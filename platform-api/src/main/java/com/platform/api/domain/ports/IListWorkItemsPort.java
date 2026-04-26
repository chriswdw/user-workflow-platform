package com.platform.api.domain.ports;

import com.platform.domain.model.WorkItem;

import java.util.List;

public interface IListWorkItemsPort {
    List<WorkItem> findByTenantAndWorkflowType(String tenantId, String workflowType);
}
