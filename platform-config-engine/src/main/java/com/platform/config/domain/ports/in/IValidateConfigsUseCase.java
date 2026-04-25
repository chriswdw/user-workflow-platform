package com.platform.config.domain.ports.in;

import com.platform.config.domain.model.ConfigValidationResult;

/**
 * Input port: run cross-schema validation for all active config documents belonging to a tenant.
 * Returns a ConfigValidationResult containing zero or more violations.
 * Never throws — all findings are captured in the result.
 */
public interface IValidateConfigsUseCase {

    ConfigValidationResult validate(String tenantId);
}
