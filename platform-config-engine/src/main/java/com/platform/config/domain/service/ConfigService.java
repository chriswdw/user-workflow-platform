package com.platform.config.domain.service;

import com.platform.config.domain.exception.ConfigIntegrityException;
import com.platform.config.domain.exception.ConfigNotFoundException;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.model.ConfigValidationResult;
import com.platform.config.domain.model.ConfigValidationViolation;
import com.platform.config.domain.ports.in.ILoadConfigUseCase;
import com.platform.config.domain.ports.in.IValidateConfigsUseCase;
import com.platform.config.domain.ports.out.IConfigDocumentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain service implementing config loading and cross-schema validation.
 * No framework dependencies — constructor-injected output port only.
 *
 * Loading invariant: exactly one active document must exist per (tenantId, workflowType, configType).
 * Zero → ConfigNotFoundException. More than one → ConfigIntegrityException.
 *
 * Validation checks:
 * 1. No duplicate active configs per (configType, workflowType)
 * 2. routing-config.defaultGroup references an active resolution-group
 */
public class ConfigService implements ILoadConfigUseCase, IValidateConfigsUseCase {

    private final IConfigDocumentRepository repository;

    public ConfigService(IConfigDocumentRepository repository) {
        this.repository = repository;
    }

    // ── ILoadConfigUseCase ───────────────────────────────────────────────────

    @Override
    public ConfigDocument loadActive(String tenantId, String workflowType, ConfigType configType) {
        List<ConfigDocument> active = repository
                .findByTenantAndWorkflowTypeAndType(tenantId, workflowType, configType)
                .stream()
                .filter(ConfigDocument::active)
                .toList();

        if (active.isEmpty()) {
            throw new ConfigNotFoundException(
                    "No active " + configType + " for tenantId=" + tenantId
                    + ", workflowType=" + workflowType);
        }
        if (active.size() > 1) {
            throw new ConfigIntegrityException(
                    "Multiple active " + configType + " found for tenantId=" + tenantId
                    + ", workflowType=" + workflowType + " — exactly one is required");
        }
        return active.get(0);
    }

    // ── IValidateConfigsUseCase ──────────────────────────────────────────────

    @Override
    public ConfigValidationResult validate(String tenantId) {
        List<ConfigValidationViolation> violations = new ArrayList<>();
        List<ConfigDocument> allActive = repository.findAllActiveByTenant(tenantId);

        checkDuplicateActiveConfigs(allActive, violations);
        checkRoutingConfigDefaultGroups(allActive, violations, tenantId);

        return new ConfigValidationResult(violations);
    }

    // ── Validation helpers ───────────────────────────────────────────────────

    /**
     * Detects multiple active documents of the same (configType, workflowType) combination.
     */
    private static void checkDuplicateActiveConfigs(List<ConfigDocument> active,
                                                     List<ConfigValidationViolation> violations) {
        Map<String, Long> counts = active.stream()
                .collect(Collectors.groupingBy(
                        d -> d.configType() + ":" + d.workflowType(),
                        Collectors.counting()));

        for (ConfigDocument doc : active) {
            String key = doc.configType() + ":" + doc.workflowType();
            if (counts.get(key) > 1) {
                violations.add(new ConfigValidationViolation(
                        "duplicate active " + doc.configType()
                        + " for workflowType=" + doc.workflowType(),
                        doc.configType(), doc.tenantId(), doc.workflowType()));
                // Remove to avoid reporting the same duplicate pair twice
                counts.put(key, 1L);
            }
        }
    }

    /**
     * Verifies each active routing-config's defaultGroup references a known active resolution-group.
     */
    private static void checkRoutingConfigDefaultGroups(List<ConfigDocument> active,
                                                         List<ConfigValidationViolation> violations,
                                                         String tenantId) {
        Set<String> activeGroupIds = active.stream()
                .filter(d -> d.configType() == ConfigType.RESOLUTION_GROUP)
                .map(d -> (String) d.content().get("id"))
                .collect(Collectors.toSet());

        active.stream()
                .filter(d -> d.configType() == ConfigType.ROUTING_CONFIG)
                .forEach(routingDoc -> {
                    String defaultGroup = (String) routingDoc.content().get("defaultGroup");
                    if (defaultGroup != null && !activeGroupIds.contains(defaultGroup)) {
                        violations.add(new ConfigValidationViolation(
                                "routing config defaultGroup '" + defaultGroup
                                + "' does not reference an active resolution group",
                                ConfigType.ROUTING_CONFIG,
                                tenantId,
                                routingDoc.workflowType()));
                    }
                });
    }
}
