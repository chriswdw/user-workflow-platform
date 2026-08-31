package com.platform.routing.domain.service;

import com.platform.routing.domain.exception.RoutingConfigNotFoundException;
import com.platform.routing.domain.model.RoutingConfig;
import com.platform.routing.domain.model.RoutingResult;
import com.platform.routing.domain.model.RoutingRule;
import com.platform.routing.domain.model.WorkItemToRoute;
import com.platform.routing.doubles.InMemoryAuditRepository;
import com.platform.routing.doubles.InMemoryRoutingConfigRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String WORKFLOW_TYPE = "TRADE_BREAK";

    private final InMemoryRoutingConfigRepository configRepo = new InMemoryRoutingConfigRepository();
    private final InMemoryAuditRepository auditRepo = new InMemoryAuditRepository();
    private final RoutingService service = new RoutingService(configRepo, auditRepo, new SimpleMeterRegistry());

    private WorkItemToRoute workItem(Map<String, Object> fields) {
        return new WorkItemToRoute("wi-1", TENANT, WORKFLOW_TYPE, "maker-1", fields);
    }

    @BeforeEach
    void setUp() {
        configRepo.save(new RoutingConfig(
                "cfg-1", TENANT, WORKFLOW_TYPE, "default-group", false,
                List.of(
                        new RoutingRule("rule-1", "high-priority", 1, null, "group-senior", true),
                        new RoutingRule("rule-2", "normal", 2, null, "group-ops", true),
                        new RoutingRule("rule-inactive", "inactive", 3, null, "group-x", false)
                )));
    }

    @Test
    void route_firstActiveRuleMatches_returnsMatchedGroup() {
        RoutingResult result = service.route(workItem(Map.of()));

        // No conditions on rule-1 — ConditionEvaluator with null conditions always matches
        assertThat(result.assignedGroupId()).isEqualTo("group-senior");
        assertThat(result.routedByDefault()).isFalse();
        assertThat(result.matchedRuleId()).isEqualTo("rule-1");
    }

    @Test
    void route_inactiveRulesSkipped_onlyActiveRulesEvaluated() {
        // Disable first two active rules by removing them from config
        configRepo.save(new RoutingConfig(
                "cfg-1", TENANT, WORKFLOW_TYPE, "default-group", false,
                List.of(new RoutingRule("rule-inactive", "inactive", 1, null, "group-x", false))));

        RoutingResult result = service.route(workItem(Map.of()));

        assertThat(result.routedByDefault()).isTrue();
        assertThat(result.assignedGroupId()).isEqualTo("default-group");
    }

    @Test
    void route_noRulesMatch_fallsBackToDefault() {
        configRepo.save(new RoutingConfig(
                "cfg-1", TENANT, WORKFLOW_TYPE, "default-group", false, List.of()));

        RoutingResult result = service.route(workItem(Map.of()));

        assertThat(result.assignedGroupId()).isEqualTo("default-group");
        assertThat(result.routedByDefault()).isTrue();
        assertThat(result.matchedRuleId()).isNull();
    }

    @Test
    void route_noConfigForTenant_throwsRoutingConfigNotFoundException() {
        WorkItemToRoute item = new WorkItemToRoute("wi-2", "unknown-tenant", WORKFLOW_TYPE, "user", Map.of());

        assertThatThrownBy(() -> service.route(item))
                .isInstanceOf(RoutingConfigNotFoundException.class)
                .hasMessageContaining(WORKFLOW_TYPE)
                .hasMessageContaining("unknown-tenant");
    }

    @Test
    void route_recordsAuditEntry_onMatch() {
        service.route(workItem(Map.of()));

        assertThat(auditRepo.all()).hasSize(1);
        assertThat(auditRepo.all().get(0).workItemId()).isEqualTo("wi-1");
    }

    @Test
    void route_recordsAuditEntry_onFallback() {
        configRepo.save(new RoutingConfig(
                "cfg-1", TENANT, WORKFLOW_TYPE, "default-group", false, List.of()));

        service.route(workItem(Map.of()));

        assertThat(auditRepo.all()).hasSize(1);
    }
}
