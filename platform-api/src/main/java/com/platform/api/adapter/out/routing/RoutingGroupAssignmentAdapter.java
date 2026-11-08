package com.platform.api.adapter.out.routing;

import com.platform.ingestion.domain.exception.GroupAssignmentException;
import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;
import com.platform.routing.domain.model.RoutingResult;
import com.platform.routing.domain.model.WorkItemToRoute;
import com.platform.routing.domain.ports.in.IRouteWorkItemUseCase;

/**
 * Output adapter for ingestion's {@link IGroupAssignmentPort}: delegates the decision to the
 * routing module's {@link IRouteWorkItemUseCase}, which evaluates the tenant's routing config. No
 * routing decision is made here.
 *
 * <p>A routing failure (no config for the tenant and workflow type, a rule that cannot be
 * evaluated) is rethrown as a {@link GroupAssignmentException} that adds the work item, tenant and
 * workflow type and keeps the original as its cause. It is not logged here: the consumer's error
 * handler logs the failure once, with that context. Ingestion must not guess a group, so the record
 * fails and is dead-lettered for replay once the configuration is fixed.
 */
public class RoutingGroupAssignmentAdapter implements IGroupAssignmentPort {

  private final IRouteWorkItemUseCase routing;

  public RoutingGroupAssignmentAdapter(IRouteWorkItemUseCase routing) {
    this.routing = routing;
  }

  @Override
  public AssignmentResult assignGroup(AssignmentRequest request) {
    try {
      RoutingResult result =
          routing.route(
              new WorkItemToRoute(
                  request.workItemId(),
                  request.tenantId(),
                  request.workflowType(),
                  request.makerUserId(),
                  request.fields()));
      return new AssignmentResult(result.assignedGroupId(), result.routedByDefault());
    } catch (RuntimeException e) {
      throw new GroupAssignmentException(
          request.workItemId(), request.tenantId(), request.workflowType(), e);
    }
  }
}
