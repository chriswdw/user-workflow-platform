package com.platform.api.doubles;

import com.platform.config.domain.exception.SourceConnectionNotFoundException;
import com.platform.config.domain.ports.in.IListSourceConnectionsUseCase;
import com.platform.config.domain.ports.in.IManageSourceConnectionsUseCase;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;

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
    SourceConnection saved =
        new SourceConnection(
            UUID.randomUUID().toString(),
            connection.name(),
            connection.displayName(),
            connection.connectionType(),
            connection.config(),
            connection.credentialsRef(),
            SourceConnection.INITIAL_VERSION,
            connection.createdBy(),
            now,
            now);
    connections.add(saved);
    return saved;
  }

  /**
   * Mirrors the real service plus adapter: a caller's expected version must match the stored one,
   * otherwise the write is rejected as Spring's OptimisticLockingFailureException (mapped to 409 by
   * GlobalExceptionHandler); on success the stored version advances by one.
   */
  @Override
  public SourceConnection update(SourceConnection connection) {
    SourceConnection existing =
        connections.stream()
            .filter(c -> c.id().equals(connection.id()))
            .findFirst()
            .orElseThrow(() -> new SourceConnectionNotFoundException(connection.id()));
    if (existing.version() != connection.version()) {
      throw new OptimisticLockingFailureException(
          "SourceConnection "
              + connection.id()
              + " was modified concurrently (expected version "
              + connection.version()
              + ")");
    }
    SourceConnection updated =
        new SourceConnection(
            existing.id(),
            connection.name() != null ? connection.name() : existing.name(),
            connection.displayName() != null ? connection.displayName() : existing.displayName(),
            connection.connectionType() != null
                ? connection.connectionType()
                : existing.connectionType(),
            connection.config() != null ? connection.config() : existing.config(),
            connection.credentialsRef() != null
                ? connection.credentialsRef()
                : existing.credentialsRef(),
            existing.version() + 1,
            existing.createdBy(),
            existing.createdAt(),
            OffsetDateTime.now(ZoneOffset.UTC));
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
