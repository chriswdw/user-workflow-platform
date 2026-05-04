package com.platform.config.domain.ports.out;

import com.platform.domain.model.AuditEntry;

public interface ISubmissionAuditRepository {
    void save(AuditEntry entry);
}
