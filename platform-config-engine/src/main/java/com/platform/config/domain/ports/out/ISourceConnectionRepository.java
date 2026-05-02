package com.platform.config.domain.ports.out;

import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import com.platform.domain.model.SourceConnectionAccess;

import java.util.List;
import java.util.Optional;

public interface ISourceConnectionRepository {
    SourceConnection save(SourceConnection connection);
    Optional<SourceConnection> findById(String id);
    List<SourceConnection> findAll();
    List<SourceConnection> findAccessibleByTenantAndType(String tenantId, ConnectionType type);
    void grantAccess(SourceConnectionAccess access);
    void revokeAccess(String sourceConnectionId, String tenantId);
    void delete(String id);
    boolean hasAccess(String sourceConnectionId, String tenantId);
}
