package com.platform.config.domain.ports.in;

import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;

import java.util.List;

public interface IListSourceConnectionsUseCase {
    List<SourceConnection> listAccessible(String tenantId, ConnectionType connectionType);
    List<SourceConnection> listAll();
}
