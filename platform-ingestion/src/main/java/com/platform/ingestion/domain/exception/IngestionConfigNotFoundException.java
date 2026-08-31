package com.platform.ingestion.domain.exception;

import com.platform.domain.model.SourceType;

public class IngestionConfigNotFoundException extends RuntimeException {
    public IngestionConfigNotFoundException(String tenantId, String workflowType, SourceType sourceType) {
        super("No ingestion config for tenantId=" + tenantId
                + ", workflowType=" + workflowType
                + ", sourceType=" + sourceType);
    }
}
