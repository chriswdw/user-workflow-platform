package com.platform.ingestion.domain.ports.out;

import java.util.Map;

/**
 * Output port: assign a resolution group to a work item based on routing rules.
 * In production, delegates to platform-routing's IRouteWorkItemUseCase.
 * In tests, the in-memory double returns a preconfigured group.
 */
public interface IGroupAssignmentPort {

    /**
     * Returns the assigned group id and whether it was routed by the default fallback.
     */
    AssignmentResult assignGroup(String tenantId, String workflowType, Map<String, Object> fields);

    record AssignmentResult(String groupId, boolean routedByDefault) {}
}
