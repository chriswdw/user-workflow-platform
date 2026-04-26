package com.platform.api.domain.ports;

import com.platform.domain.model.WorkItem;

import java.util.Optional;

public interface IFindWorkItemPort {
    Optional<WorkItem> findById(String tenantId, String workItemId);
}
