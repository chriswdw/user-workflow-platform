package com.platform.api.doubles;

import com.platform.audit.domain.model.AuditQuery;
import com.platform.audit.domain.ports.in.IQueryAuditTrailUseCase;
import com.platform.domain.model.AuditEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryAuditQueryPort implements IQueryAuditTrailUseCase {

    private final Map<String, List<AuditEntry>> store = new HashMap<>();

    public void setTrail(String tenantId, String workItemId, List<AuditEntry> entries) {
        store.put(tenantId + ":" + workItemId, entries);
    }

    @Override
    public List<AuditEntry> query(AuditQuery query) {
        return store.getOrDefault(query.tenantId() + ":" + query.workItemId(), List.of());
    }
}
