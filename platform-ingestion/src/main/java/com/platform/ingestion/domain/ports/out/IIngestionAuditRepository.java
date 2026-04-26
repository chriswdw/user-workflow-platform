package com.platform.ingestion.domain.ports.out;

import com.platform.domain.model.AuditEntry;

public interface IIngestionAuditRepository {

    void save(AuditEntry entry);
}
