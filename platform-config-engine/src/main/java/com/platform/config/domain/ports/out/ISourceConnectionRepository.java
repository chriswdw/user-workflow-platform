package com.platform.config.domain.ports.out;

import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import com.platform.domain.model.SourceConnectionAccess;
import java.util.List;
import java.util.Optional;

public interface ISourceConnectionRepository {
  /** Stores a new connection. Its version is the one given (SourceConnection.INITIAL_VERSION). */
  SourceConnection insert(SourceConnection connection);

  /**
   * Applies an update only if the stored version equals {@code connection.version()}, the version
   * the caller last read, then advances the stored version by one and returns the stored row. A
   * stale (or missing) row is rejected by the adapter, never silently overwritten.
   */
  SourceConnection update(SourceConnection connection);

  Optional<SourceConnection> findById(String id);

  List<SourceConnection> findAll();

  List<SourceConnection> findAccessibleByTenantAndType(String tenantId, ConnectionType type);

  void grantAccess(SourceConnectionAccess access);

  void revokeAccess(String sourceConnectionId, String tenantId);

  void delete(String id);

  boolean hasAccess(String sourceConnectionId, String tenantId);
}
