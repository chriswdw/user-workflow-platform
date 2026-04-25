package com.platform.routing.domain.service;

import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
import com.platform.routing.domain.model.RoutingConfig;
import com.platform.routing.domain.model.RoutingResult;
import com.platform.routing.domain.model.RoutingRule;
import com.platform.routing.domain.model.WorkItemToRoute;
import com.platform.routing.domain.ports.in.IRouteWorkItemUseCase;
import com.platform.routing.domain.ports.out.IAuditRepository;
import com.platform.routing.domain.ports.out.IRoutingConfigRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Domain service implementing the routing use case.
 * No framework dependencies — constructor-injected output ports only.
 *
 * Algorithm:
 * 1. Load active RoutingConfig for (tenantId, workflowType)
 * 2. Evaluate active rules in ascending priority order; first match wins
 * 3. If no rule matches, assign defaultGroup and set routedByDefault=true
 * 4. Write AuditEntry (ASSIGNMENT or ROUTING_FALLBACK)
 *
 * DOMAIN INVARIANT: assignedGroupId is never null. An unrouted work item is not permitted.
 */
public class RoutingService implements IRouteWorkItemUseCase {

    private final IRoutingConfigRepository routingConfigRepository;
    private final IAuditRepository auditRepository;

    public RoutingService(IRoutingConfigRepository routingConfigRepository,
                          IAuditRepository auditRepository) {
        this.routingConfigRepository = routingConfigRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public RoutingResult route(WorkItemToRoute workItem) {
        RoutingConfig config = routingConfigRepository
                .findActiveByTenantAndWorkflowType(workItem.tenantId(), workItem.workflowType())
                .orElseThrow(() -> new IllegalStateException(
                        "No active routing config for workflowType=" + workItem.workflowType()
                        + ", tenantId=" + workItem.tenantId()));

        for (RoutingRule rule : activeRulesInPriorityOrder(config)) {
            if (ConditionEvaluator.evaluate(rule.conditions(), workItem.fields())) {
                auditRepository.save(auditEntry(workItem, AuditEventType.ASSIGNMENT, rule.targetGroupId()));
                return new RoutingResult(rule.targetGroupId(), false, rule.id());
            }
        }

        // No rule matched — fall back to defaultGroup
        auditRepository.save(auditEntry(workItem, AuditEventType.ROUTING_FALLBACK, config.defaultGroupId()));
        return new RoutingResult(config.defaultGroupId(), true, null);
    }

    private static List<RoutingRule> activeRulesInPriorityOrder(RoutingConfig config) {
        return config.rules().stream()
                .filter(RoutingRule::active)
                .sorted(Comparator.comparingInt(RoutingRule::priority))
                .toList();
    }

    private static AuditEntry auditEntry(WorkItemToRoute workItem,
                                         AuditEventType eventType,
                                         String assignedGroupId) {
        return new AuditEntry(
                UUID.randomUUID().toString(),
                workItem.tenantId(),
                workItem.id(),
                workItem.id(),   // correlationId — full WorkItem carries its own; id is a stand-in here
                eventType,
                null,            // previousState — routing is not a state transition
                null,            // newState      — routing is not a state transition
                null,
                List.of(new AuditEntry.ChangedField("assignedGroup", null, assignedGroupId)),
                workItem.makerUserId(),
                "SYSTEM",
                Instant.now(),
                workItem.id() + ":" + eventType.name()
        );
    }
}
