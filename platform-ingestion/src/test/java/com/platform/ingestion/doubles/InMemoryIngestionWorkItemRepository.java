package com.platform.ingestion.doubles;

import com.platform.domain.model.WorkItem;
import com.platform.ingestion.domain.ports.out.IIngestionWorkItemRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryIngestionWorkItemRepository implements IIngestionWorkItemRepository {

    private final List<WorkItem> saved = new ArrayList<>();

    @Override
    public WorkItem save(WorkItem workItem) {
        saved.add(workItem);
        return workItem;
    }

    public List<WorkItem> all() {
        return Collections.unmodifiableList(saved);
    }
}
