package com.platform.api.doubles;

import com.platform.config.domain.ports.in.IListSourceConnectionsUseCase;
import com.platform.config.domain.ports.in.IManageSourceConnectionsUseCase;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import com.platform.domain.model.SourceConnectionAccess;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class InMemorySourceConnectionPort
        implements IManageSourceConnectionsUseCase, IListSourceConnectionsUseCase {

    private final List<SourceConnection> connections = new ArrayList<>();
    private final Set<String> accessGrants = new HashSet<>();

    public void putConnection(SourceConnection connection) {
        connections.removeIf(c -> c.id().equals(connection.id()));
        connections.add(connection);
    }

    public void grantAccessForTest(String connectionId, String tenantId) {
        accessGrants.add(connectionId + ":" + tenantId);
    }

    public void reset() {
        connections.clear();
        accessGrants.clear();
    }

    @Override
    public SourceConnection create(SourceConnection connection) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SourceConnection saved = new SourceConnection(
                UUID.randomUUID().toString(),
                connection.name(),
                connection.displayName(),
                connection.connectionType(),
                connection.config(),
                connection.credentialsRef(),
                connection.createdBy(),
                now, now);
        connections.add(saved);
        return saved;
    }

    @Override
    public SourceConnection update(SourceConnection connection) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Optional<SourceConnection> existing = connections.stream()
                .filter(c -> c.id().equals(connection.id()))
                .findFirst();
        SourceConnection updated = new SourceConnection(
                connection.id(),
                connection.name() != null ? connection.name() : existing.map(SourceConnection::name).orElse(null),
                connection.displayName() != null ? connection.displayName() : existing.map(SourceConnection::displayName).orElse(null),
                connection.connectionType() != null ? connection.connectionType() : existing.map(SourceConnection::connectionType).orElse(null),
                connection.config() != null ? connection.config() : existing.map(SourceConnection::config).orElse(Map.of()),
                connection.credentialsRef() != null ? connection.credentialsRef() : existing.map(SourceConnection::credentialsRef).orElse(null),
                existing.map(SourceConnection::createdBy).orElse(null),
                existing.map(SourceConnection::createdAt).orElse(now),
                now);
        connections.removeIf(c -> c.id().equals(connection.id()));
        connections.add(updated);
        return updated;
    }

    @Override
    public void delete(String connectionId) {
        connections.removeIf(c -> c.id().equals(connectionId));
        accessGrants.removeIf(k -> k.startsWith(connectionId + ":"));
    }

    @Override
    public void grantAccess(String connectionId, String tenantId, String grantedBy) {
        accessGrants.add(connectionId + ":" + tenantId);
    }

    @Override
    public void revokeAccess(String connectionId, String tenantId) {
        accessGrants.remove(connectionId + ":" + tenantId);
    }

    @Override
    public List<SourceConnection> listAccessible(String tenantId, ConnectionType connectionType) {
        return connections.stream()
                .filter(c -> accessGrants.contains(c.id() + ":" + tenantId))
                .filter(c -> connectionType == null || c.connectionType() == connectionType)
                .toList();
    }

    @Override
    public List<SourceConnection> listAll() {
        return List.copyOf(connections);
    }
}
