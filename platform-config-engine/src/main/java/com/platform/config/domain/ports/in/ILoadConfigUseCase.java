package com.platform.config.domain.ports.in;

import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;

/**
 * Input port: load the single active config document for a given tenant, workflowType, and config type.
 * Throws ConfigNotFoundException if none is active.
 * Throws ConfigIntegrityException if more than one is active (domain invariant violated).
 */
public interface ILoadConfigUseCase {

    ConfigDocument loadActive(String tenantId, String workflowType, ConfigType configType);
}
