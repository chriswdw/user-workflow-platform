package com.platform.config.domain.ports.out;

import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;

import java.util.List;

public interface IConfigDocumentRepository {

    /**
     * Returns all documents (active and inactive) for the given tenant, workflowType, and config type.
     * Used by the load operation — the service checks for exactly one active among the returned list.
     */
    List<ConfigDocument> findByTenantAndWorkflowTypeAndType(String tenantId,
                                                             String workflowType,
                                                             ConfigType configType);

    /**
     * Returns all ACTIVE documents across all config types for the given tenant.
     * Used by validateConfigs for cross-schema constraint checking.
     */
    List<ConfigDocument> findAllActiveByTenant(String tenantId);
}
