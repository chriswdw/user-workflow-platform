package com.platform.ingestion.doubles;

import com.platform.domain.model.AuditEntry;
import com.platform.ingestion.domain.ports.out.IIngestionAuditRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryIngestionAuditRepository implements IIngestionAuditRepository {

    private final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void save(AuditEntry entry) {
        entries.add(entry);
    }

    public List<AuditEntry> all() {
        return Collections.unmodifiableList(entries);
    }
}
