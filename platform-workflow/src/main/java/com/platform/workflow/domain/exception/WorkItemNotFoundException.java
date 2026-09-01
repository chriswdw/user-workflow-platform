package com.platform.workflow.domain.exception;

public class WorkItemNotFoundException extends RuntimeException {
  public WorkItemNotFoundException(String workItemId) {
    super("WorkItem not found: " + workItemId);
  }
}
