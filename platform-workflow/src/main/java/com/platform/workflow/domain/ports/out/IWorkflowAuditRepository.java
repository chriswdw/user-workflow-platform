package com.platform.workflow.domain.ports.out;

import com.platform.domain.model.AuditEntry;

public interface IWorkflowAuditRepository {

    void save(AuditEntry entry);
}
