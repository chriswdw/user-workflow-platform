package com.platform.audit.domain.ports.in;

import com.platform.audit.domain.model.AuditQuery;
import com.platform.domain.model.AuditEntry;

import java.util.List;

public interface IQueryAuditTrailUseCase {
    List<AuditEntry> query(AuditQuery query);
}
