package com.platform.ingestion.domain.ports.out;

public interface IIdempotencyKeyRepository {

    boolean exists(String tenantId, String workflowType, String idempotencyKey);

    void save(String tenantId, String workflowType, String idempotencyKey);
}
