package com.platform.config.domain.model;

import java.util.List;

public record ConfigValidationResult(
        List<ConfigValidationViolation> violations
) {
    public boolean isValid() {
        return violations.isEmpty();
    }
}
