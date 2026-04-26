package com.platform.ingestion.domain.ports.out;

import com.platform.domain.model.SourceType;
import com.platform.ingestion.domain.model.IngestionConfig;

import java.util.Optional;

public interface IIngestionConfigRepository {

    Optional<IngestionConfig> findByTenantAndWorkflowTypeAndSourceType(
            String tenantId, String workflowType, SourceType sourceType);
}
