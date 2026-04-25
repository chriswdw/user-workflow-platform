package com.platform.routing.domain.service;

import com.platform.routing.domain.model.RoutingResult;
import com.platform.routing.domain.model.WorkItemToRoute;
import com.platform.routing.domain.ports.in.IRouteWorkItemUseCase;
import com.platform.routing.domain.ports.out.IAuditRepository;
import com.platform.routing.domain.ports.out.IRoutingConfigRepository;

/**
 * Domain service implementing the routing use case.
 * No framework dependencies — constructor-injected output ports only.
 *
 * Algorithm:
 * 1. Load active RoutingConfig for (tenantId, workflowType)
 * 2. Evaluate rules in ascending priority order; return first match
 * 3. If no rule matches, assign defaultGroup and set routedByDefault=true
 * 4. Write AuditEntry (ASSIGNMENT or ROUTING_FALLBACK)
 *
 * DOMAIN INVARIANT: assignedGroupId is never null. An unrouted work item is not permitted.
 */
public class RoutingService implements IRouteWorkItemUseCase {

    private final IRoutingConfigRepository routingConfigRepository;
    private final IAuditRepository auditRepository;

    public RoutingService(IRoutingConfigRepository routingConfigRepository,
                          IAuditRepository auditRepository) {
        this.routingConfigRepository = routingConfigRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public RoutingResult route(WorkItemToRoute workItem) {
        throw new UnsupportedOperationException("RoutingService not yet implemented — RED state expected");
    }
}
