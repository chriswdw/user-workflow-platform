package com.platform.workflow.domain.ports.out;

import com.platform.domain.model.WorkItem;

import java.util.Optional;

public interface IWorkItemRepository {

    Optional<WorkItem> findById(String tenantId, String workItemId);

    WorkItem save(WorkItem workItem);
}
