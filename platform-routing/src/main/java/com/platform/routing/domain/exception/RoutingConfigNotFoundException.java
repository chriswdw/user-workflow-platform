package com.platform.routing.domain.exception;

public class RoutingConfigNotFoundException extends RuntimeException {
    public RoutingConfigNotFoundException(String workflowType, String tenantId) {
        super("No active routing config for workflowType=" + workflowType + ", tenantId=" + tenantId);
    }
}
