package com.platform.config.doubles;

import com.platform.config.domain.ports.out.ISubmissionAuditRepository;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;

import java.util.ArrayList;
import java.util.List;

public class InMemorySubmissionAuditRepository implements ISubmissionAuditRepository {

    private final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void save(AuditEntry entry) {
        entries.add(entry);
    }

    public List<AuditEntry> findBySubmissionId(String submissionId) {
        return entries.stream()
                .filter(e -> submissionId.equals(e.workItemId()))
                .toList();
    }

    public List<AuditEntry> findByEventType(AuditEventType type) {
        return entries.stream()
                .filter(e -> e.eventType() == type)
                .toList();
    }

    public void reset() {
        entries.clear();
    }
}
