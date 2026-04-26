package com.platform.audit.doubles;

import com.platform.audit.domain.ports.out.IAuditEntryRepository;
import com.platform.domain.model.AuditEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InMemoryAuditEntryRepository implements IAuditEntryRepository {

    private final List<AuditEntry> store = new ArrayList<>();

    @Override
    public void save(AuditEntry entry) {
        store.add(entry);
    }

    @Override
    public List<AuditEntry> findByTenantAndWorkItemId(String tenantId, String workItemId) {
        return store.stream()
                .filter(e -> tenantId.equals(e.tenantId()) && workItemId.equals(e.workItemId()))
                .sorted(Comparator.comparing(AuditEntry::timestamp))
                .toList();
    }
}
