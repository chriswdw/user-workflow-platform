package com.platform.api.adapter.out.routing;

import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;
import com.platform.routing.domain.model.RoutingResult;
import com.platform.routing.domain.model.WorkItemToRoute;
import com.platform.routing.domain.ports.in.IRouteWorkItemUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Output adapter for ingestion's {@link IGroupAssignmentPort}: delegates the decision to the
 * routing module's {@link IRouteWorkItemUseCase}, which evaluates the tenant's routing config. No
 * routing decision is made here.
 *
 * <p>A routing failure (no config for the tenant and workflow type, a rule that cannot be
 * evaluated) is logged and rethrown unchanged. Ingestion must not guess a group, so the record
 * fails and is dead-lettered for replay once the configuration is fixed.
 */
public class RoutingGroupAssignmentAdapter implements IGroupAssignmentPort {

  private static final Logger log = LoggerFactory.getLogger(RoutingGroupAssignmentAdapter.class);

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
      // Identifiers and the exception type only: messages and field values can carry personal or
      // financial data and must stay out of logs.
      log.error(
          "Routing failed workItemId={} tenantId={} workflowType={} cause={}",
          request.workItemId(),
          request.tenantId(),
          request.workflowType(),
          e.getClass().getSimpleName());
      throw e;
    }
  }
}
