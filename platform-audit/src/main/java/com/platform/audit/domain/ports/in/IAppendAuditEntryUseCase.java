package com.platform.audit.domain.ports.in;

import com.platform.domain.model.AuditEntry;

public interface IAppendAuditEntryUseCase {
    void append(AuditEntry entry);
}
