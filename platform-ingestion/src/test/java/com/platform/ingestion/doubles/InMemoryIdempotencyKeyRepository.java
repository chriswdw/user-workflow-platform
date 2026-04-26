package com.platform.ingestion.doubles;

import com.platform.ingestion.domain.ports.out.IIdempotencyKeyRepository;

import java.util.HashSet;
import java.util.Set;

public class InMemoryIdempotencyKeyRepository implements IIdempotencyKeyRepository {

    private final Set<String> keys = new HashSet<>();

    @Override
    public boolean exists(String tenantId, String workflowType, String idempotencyKey) {
        return keys.contains(key(tenantId, workflowType, idempotencyKey));
    }

    @Override
    public void save(String tenantId, String workflowType, String idempotencyKey) {
        keys.add(key(tenantId, workflowType, idempotencyKey));
    }

    private static String key(String tenantId, String workflowType, String idempotencyKey) {
        return tenantId + ":" + workflowType + ":" + idempotencyKey;
    }
}
