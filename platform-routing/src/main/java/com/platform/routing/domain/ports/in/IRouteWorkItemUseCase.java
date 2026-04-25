package com.platform.routing.domain.ports.in;

import com.platform.routing.domain.model.RoutingResult;
import com.platform.routing.domain.model.WorkItemToRoute;

/**
 * Input port: routes a work item to a resolution group.
 * Implemented by RoutingService. Called by ingestion adapters after normalisation.
 */
public interface IRouteWorkItemUseCase {
    RoutingResult route(WorkItemToRoute workItem);
}
