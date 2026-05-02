package com.platform.config.domain.service;

import com.platform.config.domain.ports.in.IListSourceConnectionsUseCase;
import com.platform.config.domain.ports.in.IManageSourceConnectionsUseCase;
import com.platform.config.domain.ports.out.ISourceConnectionRepository;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import com.platform.domain.model.SourceConnectionAccess;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public class SourceConnectionService
        implements IManageSourceConnectionsUseCase, IListSourceConnectionsUseCase {

    private final ISourceConnectionRepository repo;

    public SourceConnectionService(ISourceConnectionRepository repo) {
        this.repo = repo;
    }

    @Override
    public SourceConnection create(SourceConnection connection) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repo.save(new SourceConnection(
                UUID.randomUUID().toString(),
                connection.name(),
                connection.displayName(),
                connection.connectionType(),
                connection.config(),
                connection.credentialsRef(),
                connection.createdBy(),
                now, now));
    }

    @Override
    public SourceConnection update(SourceConnection connection) {
        SourceConnection existing = repo.findById(connection.id())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source connection not found: " + connection.id()));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repo.save(new SourceConnection(
                existing.id(),
                connection.name() != null ? connection.name() : existing.name(),
                connection.displayName() != null ? connection.displayName() : existing.displayName(),
                connection.connectionType() != null ? connection.connectionType() : existing.connectionType(),
                connection.config() != null ? connection.config() : existing.config(),
                connection.credentialsRef() != null ? connection.credentialsRef() : existing.credentialsRef(),
                existing.createdBy(),
                existing.createdAt(),
                now));
    }

    @Override
    public void delete(String connectionId) {
        repo.delete(connectionId);
    }

    @Override
    public void grantAccess(String connectionId, String tenantId, String grantedBy) {
        repo.grantAccess(new SourceConnectionAccess(
                UUID.randomUUID().toString(),
                connectionId, tenantId, grantedBy,
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Override
    public void revokeAccess(String connectionId, String tenantId) {
        repo.revokeAccess(connectionId, tenantId);
    }

    @Override
    public List<SourceConnection> listAccessible(String tenantId, ConnectionType connectionType) {
        return repo.findAccessibleByTenantAndType(tenantId, connectionType);
    }

    @Override
    public List<SourceConnection> listAll() {
        return repo.findAll();
    }
}
