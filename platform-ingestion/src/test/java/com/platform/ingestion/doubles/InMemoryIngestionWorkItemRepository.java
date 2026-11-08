package com.platform.ingestion.doubles;

import com.platform.domain.model.WorkItem;
import com.platform.ingestion.domain.ports.out.IIngestionWorkItemRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryIngestionWorkItemRepository implements IIngestionWorkItemRepository {

  private final List<WorkItem> saved = new ArrayList<>();
  private RuntimeException failure;

  /**
   * Makes every subsequent save throw, simulating a persistence failure that is not a duplicate.
   */
  public void failWith(RuntimeException failure) {
    this.failure = failure;
  }

  @Override
  public WorkItem save(WorkItem workItem) {
    if (failure != null) {
      throw failure;
    }
    saved.add(workItem);
    return workItem;
  }

  public List<WorkItem> all() {
    return Collections.unmodifiableList(saved);
  }
}
