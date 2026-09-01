package com.platform.config.doubles;

import com.platform.config.domain.ports.out.ISourceConnectionRepository;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import com.platform.domain.model.SourceConnectionAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemorySourceConnectionRepository implements ISourceConnectionRepository {

    private final List<SourceConnection> connections = new ArrayList<>();
    private final List<SourceConnectionAccess> accessGrants = new ArrayList<>();

    @Override
    public SourceConnection save(SourceConnection connection) {
        connections.removeIf(c -> c.id().equals(connection.id()));
        connections.add(connection);
        return connection;
    }

    @Override
    public Optional<SourceConnection> findById(String id) {
        return connections.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    @Override
    public List<SourceConnection> findAll() {
        return List.copyOf(connections);
    }

    @Override
    public List<SourceConnection> findAccessibleByTenantAndType(String tenantId, ConnectionType type) {
        return connections.stream()
                .filter(c -> c.connectionType() == type)
                .filter(c -> hasAccess(c.id(), tenantId))
                .toList();
    }

    @Override
    public void grantAccess(SourceConnectionAccess access) {
        accessGrants.add(access);
    }

    @Override
    public void revokeAccess(String sourceConnectionId, String tenantId) {
        accessGrants.removeIf(a -> a.sourceConnectionId().equals(sourceConnectionId)
                && a.tenantId().equals(tenantId));
    }

    @Override
    public void delete(String id) {
        connections.removeIf(c -> c.id().equals(id));
    }

    @Override
    public boolean hasAccess(String sourceConnectionId, String tenantId) {
        return accessGrants.stream()
                .anyMatch(a -> a.sourceConnectionId().equals(sourceConnectionId)
                        && a.tenantId().equals(tenantId));
    }
}
