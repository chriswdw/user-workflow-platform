package com.platform.ingestion.doubles;

import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;

import java.util.HashMap;
import java.util.Map;

public class StubGroupAssignmentPort implements IGroupAssignmentPort {

    private final Map<String, String> groupByWorkflowType = new HashMap<>();

    public void configure(String workflowType, String groupId) {
        groupByWorkflowType.put(workflowType, groupId);
    }

    @Override
    public AssignmentResult assignGroup(String tenantId, String workflowType, Map<String, Object> fields) {
        String groupId = groupByWorkflowType.getOrDefault(workflowType, "default-group");
        return new AssignmentResult(groupId, false);
    }
}
