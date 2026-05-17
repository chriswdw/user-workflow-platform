package com.platform.ingestion.doubles;

import com.platform.domain.model.AuditEntry;
import com.platform.domain.ports.out.IAuditRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryIngestionAuditRepository implements IAuditRepository {

    private final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void save(AuditEntry entry) {
        entries.add(entry);
    }

    public List<AuditEntry> all() {
        return Collections.unmodifiableList(entries);
    }
}
