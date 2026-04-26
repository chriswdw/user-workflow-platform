package com.platform.audit.domain.service;

import com.platform.audit.domain.model.AuditQuery;
import com.platform.audit.domain.ports.in.IAppendAuditEntryUseCase;
import com.platform.audit.domain.ports.in.IQueryAuditTrailUseCase;
import com.platform.audit.domain.ports.out.IAuditEntryRepository;
import com.platform.domain.model.AuditEntry;

import java.util.List;
import java.util.Objects;

public class AuditService implements IAppendAuditEntryUseCase, IQueryAuditTrailUseCase {

    private final IAuditEntryRepository repository;

    public AuditService(IAuditEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(AuditEntry entry) {
        Objects.requireNonNull(entry, "AuditEntry must not be null");
        repository.save(entry);
    }

    @Override
    public List<AuditEntry> query(AuditQuery query) {
        Objects.requireNonNull(query, "AuditQuery must not be null");
        List<AuditEntry> entries = repository.findByTenantAndWorkItemId(query.tenantId(), query.workItemId());

        return entries.stream()
                .filter(e -> query.eventTypes() == null || query.eventTypes().isEmpty()
                        || query.eventTypes().contains(e.eventType()))
                .filter(e -> query.from() == null || !e.timestamp().isBefore(query.from()))
                .filter(e -> query.to() == null || !e.timestamp().isAfter(query.to()))
                .toList();
    }
}
