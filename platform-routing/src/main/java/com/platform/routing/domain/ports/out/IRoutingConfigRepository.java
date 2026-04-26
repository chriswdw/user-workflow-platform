package com.platform.routing.domain.ports.out;

import com.platform.routing.domain.model.RoutingConfig;

import java.util.Optional;

/**
 * Output port: retrieves the active routing configuration for a workflow type.
 * Implemented by the PostgreSQL adapter in production; by InMemoryRoutingConfigRepository in tests.
 */
public interface IRoutingConfigRepository {
    Optional<RoutingConfig> findActiveByTenantAndWorkflowType(String tenantId, String workflowType);
}
