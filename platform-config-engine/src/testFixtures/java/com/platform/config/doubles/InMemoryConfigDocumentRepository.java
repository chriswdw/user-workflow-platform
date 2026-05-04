package com.platform.config.doubles;

import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.ports.out.IConfigDocumentRepository;

import java.util.ArrayList;
import java.util.List;

public class InMemoryConfigDocumentRepository implements IConfigDocumentRepository {

    private final List<ConfigDocument> store = new ArrayList<>();

    public void save(ConfigDocument doc) {
        store.add(doc);
    }

    @Override
    public List<ConfigDocument> findByTenantAndWorkflowTypeAndType(String tenantId,
                                                                     String workflowType,
                                                                     ConfigType configType) {
        return store.stream()
                .filter(d -> d.tenantId().equals(tenantId)
                          && configType.equals(d.configType())
                          && workflowTypeMatches(d.workflowType(), workflowType))
                .toList();
    }

    @Override
    public List<ConfigDocument> findAllActiveByTenant(String tenantId) {
        return store.stream()
                .filter(d -> d.tenantId().equals(tenantId) && d.active())
                .toList();
    }

    private static boolean workflowTypeMatches(String stored, String requested) {
        if (stored == null && requested == null) return true;
        if (stored == null || requested == null) return false;
        return stored.equals(requested);
    }
}
