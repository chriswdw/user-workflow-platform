package com.platform.domain.ports.out;

import com.platform.domain.model.AuditEntry;

/**
 * Canonical write-only audit output port. All domain modules inject this single interface.
 * The single AuditEntryJdbcRepository in platform-api implements it (and IAuditEntryRepository
 * for query operations). Append-only — no update or delete operations.
 */
public interface IAuditRepository {
    void save(AuditEntry entry);
}
