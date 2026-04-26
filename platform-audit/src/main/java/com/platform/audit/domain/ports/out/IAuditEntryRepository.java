package com.platform.audit.domain.ports.out;

import com.platform.domain.model.AuditEntry;

import java.util.List;

public interface IAuditEntryRepository {
    void save(AuditEntry entry);
    List<AuditEntry> findByTenantAndWorkItemId(String tenantId, String workItemId);
}
