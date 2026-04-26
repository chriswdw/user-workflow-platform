package com.platform.workflow.domain.ports.out;

import com.platform.workflow.domain.model.WorkflowConfig;

import java.util.Optional;

public interface IWorkflowConfigRepository {

    Optional<WorkflowConfig> findActiveByTenantAndWorkflowType(String tenantId, String workflowType);
}
