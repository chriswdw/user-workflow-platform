package com.platform.ingestion.domain.ports.out;

import com.platform.domain.model.WorkItem;

public interface IIngestionWorkItemRepository {

    WorkItem save(WorkItem workItem);
}
