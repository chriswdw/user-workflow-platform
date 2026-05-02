package com.platform.config.domain.ports.in;

import com.platform.domain.model.SourceConnection;

public interface IManageSourceConnectionsUseCase {
    SourceConnection create(SourceConnection connection);
    SourceConnection update(SourceConnection connection);
    void delete(String connectionId);
    void grantAccess(String connectionId, String tenantId, String grantedBy);
    void revokeAccess(String connectionId, String tenantId);
}
