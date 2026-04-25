package com.platform.workflow.doubles;

import com.platform.domain.model.AuditEntry;
import com.platform.workflow.domain.ports.out.IWorkflowAuditRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryWorkflowAuditRepository implements IWorkflowAuditRepository {

    private final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void save(AuditEntry entry) {
        entries.add(entry);
    }

    public List<AuditEntry> all() {
        return Collections.unmodifiableList(entries);
    }
}
