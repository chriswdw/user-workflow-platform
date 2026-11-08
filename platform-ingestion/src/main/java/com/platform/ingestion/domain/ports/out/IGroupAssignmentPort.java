package com.platform.ingestion.domain.ports.out;

import java.util.Map;

/**
 * Output port: assign a resolution group to a work item based on routing rules. In production,
 * delegates to platform-routing's IRouteWorkItemUseCase. In tests, the in-memory double returns a
 * preconfigured group.
 *
 * <p>The request carries the id the work item will be persisted under, so that anything the routing
 * side records (its audit entry) refers to the real work item and not to a stand-in.
 */
public interface IGroupAssignmentPort {

  /**
   * Returns the assigned group id and whether it was routed by the default fallback. Throws if the
   * item cannot be routed (for example no routing config exists): ingestion must not guess a group.
   */
  AssignmentResult assignGroup(AssignmentRequest request);

  /** What the router needs to know about the work item that is about to be created. */
  record AssignmentRequest(
      String workItemId,
      String tenantId,
      String workflowType,
      String makerUserId,
      Map<String, Object> fields) {}

  record AssignmentResult(String groupId, boolean routedByDefault) {}
}
