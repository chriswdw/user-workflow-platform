package com.platform.routing.domain.ports.out;

import com.platform.domain.model.AuditEntry;

/**
 * Output port: appends an immutable audit entry.
 * Implemented by the audit adapter in production; by InMemoryAuditRepository in tests.
 * Append-only — no update or delete operations.
 */
public interface IAuditRepository {
    void save(AuditEntry entry);
}
