package com.platform.ingestion.domain.exception;

/**
 * Thrown by the group assignment port when a work item cannot be routed (for example no routing
 * config exists for the tenant and workflow type, or a rule cannot be evaluated). Ingestion never
 * guesses a group: the exception propagates, nothing is persisted, and the record is dead-lettered
 * so it can be replayed once the cause is fixed.
 *
 * <p>The message identifies the work item, tenant and workflow type only. Field values are never
 * included, because exception messages are logged and values can be personal or financial data. The
 * underlying cause is kept for diagnosis.
 */
public class GroupAssignmentException extends RuntimeException {

  public GroupAssignmentException(
      String workItemId, String tenantId, String workflowType, Throwable cause) {
    super(
        "Could not assign a group for workItemId="
            + workItemId
            + ", tenantId="
            + tenantId
            + ", workflowType="
            + workflowType,
        cause);
  }
}
