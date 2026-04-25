package com.platform.routing.doubles;

import com.platform.domain.model.AuditEntry;
import com.platform.routing.domain.ports.out.IAuditRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory test double for IAuditRepository.
 * Captures written audit entries for assertion in Cucumber step definitions.
 */
public class InMemoryAuditRepository implements IAuditRepository {

    private final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void save(AuditEntry entry) {
        entries.add(entry);
    }

    public List<AuditEntry> all() {
        return List.copyOf(entries);
    }
}
