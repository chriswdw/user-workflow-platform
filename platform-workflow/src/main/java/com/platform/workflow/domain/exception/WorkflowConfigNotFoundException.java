package com.platform.workflow.domain.exception;

public class WorkflowConfigNotFoundException extends RuntimeException {
    public WorkflowConfigNotFoundException(String workflowType, String tenantId) {
        super("No active workflow config for workflowType=" + workflowType + ", tenantId=" + tenantId);
    }
}
