package com.platform.api.config;

import com.platform.api.domain.ports.IFindWorkItemPort;
import com.platform.api.domain.ports.IListWorkItemsPort;
import com.platform.audit.domain.model.AuditQuery;
import com.platform.audit.domain.ports.in.IQueryAuditTrailUseCase;
import com.platform.config.domain.exception.ConfigNotFoundException;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.ports.in.ILoadConfigUseCase;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
import com.platform.domain.model.SourceType;
import com.platform.domain.model.WorkItem;
import com.platform.workflow.domain.exception.ForbiddenTransitionException;
import com.platform.workflow.domain.model.TransitionCommand;
import com.platform.workflow.domain.ports.in.ITransitionWorkItemUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback implementations for all port interfaces.
 * Each bean is conditional on no other implementation being present —
 * when a real adapter (e.g. JPA + PostgreSQL) is registered, these step aside
 * automatically. No profile flag required.
 */
@Configuration
public class DevConfig {

    // ── Shared in-memory store ────────────────────────────────────────────────

    @Bean
    public ConcurrentHashMap<String, WorkItem> devWorkItemStore() {
        ConcurrentHashMap<String, WorkItem> store = new ConcurrentHashMap<>();
        seed().forEach(item -> store.put(storeKey(item.tenantId(), item.id()), item));
        return store;
    }

    @Bean
    public ConcurrentHashMap<String, List<AuditEntry>> devAuditStore() {
        ConcurrentHashMap<String, List<AuditEntry>> store = new ConcurrentHashMap<>();
        seedAudit().forEach((key, entries) -> store.put(key, entries));
        return store;
    }

    // ── Port beans ────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public IFindWorkItemPort findWorkItemPort(ConcurrentHashMap<String, WorkItem> store) {
        return (tenantId, workItemId) -> Optional.ofNullable(store.get(storeKey(tenantId, workItemId)));
    }

    @Bean
    @ConditionalOnMissingBean
    public IListWorkItemsPort listWorkItemsPort(ConcurrentHashMap<String, WorkItem> store) {
        return (tenantId, workflowType) -> store.values().stream()
                .filter(w -> tenantId.equals(w.tenantId()) && workflowType.equals(w.workflowType()))
                .toList();
    }

    @Bean
    @ConditionalOnMissingBean
    public ITransitionWorkItemUseCase transitionWorkItemUseCase(ConcurrentHashMap<String, WorkItem> store) {
        return (TransitionCommand cmd) -> {
            String key = storeKey(cmd.tenantId(), cmd.workItemId());
            WorkItem current = store.get(key);
            if (current == null) throw new IllegalStateException("Work item not found: " + cmd.workItemId());
            if ("READ_ONLY".equals(cmd.actorRole()))
                throw new ForbiddenTransitionException("Role READ_ONLY cannot trigger transitions");

            // Simple dev state machine: apply the named transition
            String newStatus = applyTransition(current.status(), cmd.transitionName());
            WorkItem updated = current.withStatus(newStatus).withMakerUserId(cmd.actorUserId());
            store.put(key, updated);
            return updated;
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public IQueryAuditTrailUseCase queryAuditTrailUseCase(ConcurrentHashMap<String, List<AuditEntry>> store) {
        return (AuditQuery query) -> {
            String key = storeKey(query.tenantId(), query.workItemId());
            return store.getOrDefault(key, List.of());
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ILoadConfigUseCase loadConfigUseCase() {
        return (tenantId, workflowType, configType) -> {
            if (ConfigType.DETAIL_VIEW_CONFIG.equals(configType)
                    && "SETTLEMENT_EXCEPTION".equals(workflowType)) {
                return new ConfigDocument(
                        "dev-dvc-settlement", tenantId, workflowType,
                        ConfigType.DETAIL_VIEW_CONFIG, seedDetailViewConfig(),
                        "1", true);
            }
            throw new ConfigNotFoundException(
                    "No dev config for " + configType + "/" + workflowType);
        };
    }

    private static Map<String, Object> seedDetailViewConfig() {
        return Map.of(
            "id",           "dev-dvc-settlement",
            "tenantId",     "tenant-1",
            "workflowType", "SETTLEMENT_EXCEPTION",
            "active",       true,
            "version",      1,
            "sections", List.of(
                Map.of("title", "Trade Details", "layout", "TWO_COLUMN", "fields", List.of(
                    Map.of("field", "trade.ref",                     "label", "Trade Ref",   "formatter", "TEXT"),
                    Map.of("field", "trade.valueDate",               "label", "Value Date",  "formatter", "DATE"),
                    Map.of("field", "trade.notionalAmount.amount",   "label", "Notional",    "formatter", "CURRENCY"),
                    Map.of("field", "trade.notionalAmount.currency", "label", "Currency",    "formatter", "TEXT"),
                    Map.of("field", "status",                        "label", "Status",      "formatter", "BADGE"),
                    Map.of("field", "priorityLevel",                 "label", "Priority",    "formatter", "BADGE")
                )),
                Map.of("title", "Counterparty", "layout", "TWO_COLUMN", "fields", List.of(
                    Map.of("field", "counterparty.name", "label", "Name", "formatter", "TEXT"),
                    Map.of("field", "counterparty.lei",  "label", "LEI",  "formatter", "TEXT")
                )),
                Map.of("title", "Assignment", "layout", "TWO_COLUMN", "collapsible", true, "fields", List.of(
                    Map.of("field", "assignedGroup", "label", "Group",   "formatter", "TEXT"),
                    Map.of("field", "source",        "label", "Source",  "formatter", "TEXT"),
                    Map.of("field", "makerUserId",   "label", "Maker",   "formatter", "TEXT"),
                    Map.of("field", "createdAt",     "label", "Created", "formatter", "DATETIME")
                ))
            ),
            "actions", List.of(
                Map.of("transition", "close-as-resolved",
                       "label", "Close as Resolved", "style", "PRIMARY",
                       "visibleInStates", List.of("UNDER_REVIEW", "ESCALATED"),
                       "visibleRoles", List.of("ANALYST", "SUPERVISOR"),
                       "confirmationRequired", true,
                       "confirmationMessage", "Close this exception as resolved?",
                       "inputFields", List.of(
                           Map.of("field", "resolution.reason", "label", "Reason",
                                  "inputType", "TEXTAREA", "required", true)
                       )),
                Map.of("transition", "escalate",
                       "label", "Escalate", "style", "SECONDARY",
                       "visibleInStates", List.of("UNDER_REVIEW"),
                       "visibleRoles", List.of("ANALYST", "SUPERVISOR")),
                Map.of("transition", "assign-to-compliance",
                       "label", "Compliance Review", "style", "SECONDARY",
                       "visibleInStates", List.of("UNDER_REVIEW", "ESCALATED"),
                       "visibleRoles", List.of("SUPERVISOR")),
                Map.of("transition", "return-to-review",
                       "label", "Return to Review", "style", "SECONDARY",
                       "visibleInStates", List.of("ESCALATED"),
                       "visibleRoles", List.of("SUPERVISOR"))
            )
        );
    }

    // ── Seed data ─────────────────────────────────────────────────────────────

    private static List<WorkItem> seed() {
        Instant now = Instant.now();
        return List.of(
            item("wi-001", "tenant-1", "SETTLEMENT_EXCEPTION", SourceType.KAFKA,
                "UNDER_REVIEW", "group-ops", 750, "CRITICAL", now.minusSeconds(7200),
                Map.of(
                    "trade", Map.of(
                        "ref", "TRD-20241015-001",
                        "valueDate", "2024-10-17",
                        "notionalAmount", Map.of("amount", "15000000.00", "currency", "GBP")),
                    "counterparty", Map.of(
                        "name", "Barclays Capital",
                        "lei", "G5GSEF7VJP5I7OUK5573"))),

            item("wi-002", "tenant-1", "SETTLEMENT_EXCEPTION", SourceType.KAFKA,
                "UNDER_REVIEW", "group-ops", 400, "HIGH", now.minusSeconds(3600),
                Map.of(
                    "trade", Map.of(
                        "ref", "TRD-20241015-002",
                        "valueDate", "2024-10-18",
                        "notionalAmount", Map.of("amount", "2500000.00", "currency", "EUR")),
                    "counterparty", Map.of(
                        "name", "Deutsche Bank AG",
                        "lei", "7LTWFZYICNSX8D621K86"))),

            item("wi-003", "tenant-1", "SETTLEMENT_EXCEPTION", SourceType.DB_POLL,
                "ESCALATED", "group-senior", 850, "CRITICAL", now.minusSeconds(18000),
                Map.of(
                    "trade", Map.of(
                        "ref", "TRD-20241014-087",
                        "valueDate", "2024-10-15",
                        "notionalAmount", Map.of("amount", "50000000.00", "currency", "USD")),
                    "counterparty", Map.of(
                        "name", "JP Morgan Chase",
                        "lei", "8I5DZWZKVSZI1NUHU748"))),

            item("wi-004", "tenant-1", "SETTLEMENT_EXCEPTION", SourceType.FILE_UPLOAD,
                "CLOSED", "group-ops", 50, "LOW", now.minusSeconds(86400),
                Map.of(
                    "trade", Map.of(
                        "ref", "TRD-20241013-044",
                        "valueDate", "2024-10-14",
                        "notionalAmount", Map.of("amount", "175000.00", "currency", "GBP")),
                    "counterparty", Map.of(
                        "name", "HSBC Holdings",
                        "lei", "MLU0ZO3ML4LN2LL2TL39"))),

            item("wi-005", "tenant-1", "SETTLEMENT_EXCEPTION", SourceType.KAFKA,
                "UNDER_REVIEW", "group-ops", 300, "MEDIUM", now.minusSeconds(1800),
                Map.of(
                    "trade", Map.of(
                        "ref", "TRD-20241015-031",
                        "valueDate", "2024-10-20",
                        "notionalAmount", Map.of("amount", "8750000.00", "currency", "CHF")),
                    "counterparty", Map.of(
                        "name", "UBS Group AG",
                        "lei", "BFM8T61CT2L1QCEMIK50")))
        );
    }

    private static Map<String, List<AuditEntry>> seedAudit() {
        Instant base = Instant.now().minusSeconds(7200);
        return Map.of(
            storeKey("tenant-1", "wi-001"), List.of(
                auditEntry("tenant-1", "wi-001", AuditEventType.INGESTION,
                    null, "UNDER_REVIEW", base),
                auditEntry("tenant-1", "wi-001", AuditEventType.ASSIGNMENT,
                    "UNDER_REVIEW", "UNDER_REVIEW", base.plusSeconds(5))),
            storeKey("tenant-1", "wi-003"), List.of(
                auditEntry("tenant-1", "wi-003", AuditEventType.INGESTION,
                    null, "UNDER_REVIEW", base.minusSeconds(10800)),
                auditEntry("tenant-1", "wi-003", AuditEventType.STATE_TRANSITION,
                    "UNDER_REVIEW", "ESCALATED", base.minusSeconds(7200)),
                auditEntry("tenant-1", "wi-003", AuditEventType.SLA_WARNING,
                    "ESCALATED", "ESCALATED", base.minusSeconds(3600)))
        );
    }

    // ── Transition logic ──────────────────────────────────────────────────────

    private static String applyTransition(String currentStatus, String transitionName) {
        return switch (transitionName) {
            case "close-as-resolved"   -> "CLOSED";
            case "escalate"            -> "ESCALATED";
            case "return-to-review"    -> "UNDER_REVIEW";
            case "assign-to-compliance"-> "COMPLIANCE_REVIEW";
            default -> throw new ForbiddenTransitionException(
                    "Unknown transition '" + transitionName + "' from state '" + currentStatus + "'");
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String storeKey(String tenantId, String workItemId) {
        return tenantId + ":" + workItemId;
    }

    private static WorkItem item(String id, String tenantId, String workflowType,
                                  SourceType source, String status, String group,
                                  int priorityScore, String priorityLevel, Instant createdAt,
                                  Map<String, Object> fields) {
        return new WorkItem(id, tenantId, workflowType,
                UUID.randomUUID().toString(), null,
                source, "src-" + id, id + "-idem",
                status, group, false, fields,
                priorityScore, priorityLevel, createdAt,
                null, null, 1, "system", createdAt, createdAt);
    }

    private static AuditEntry auditEntry(String tenantId, String workItemId,
                                          AuditEventType eventType,
                                          String previousState, String newState,
                                          Instant timestamp) {
        return new AuditEntry(
                UUID.randomUUID().toString(), tenantId, workItemId,
                UUID.randomUUID().toString(), eventType,
                previousState, newState, null,
                List.of(), "system", "SYSTEM", timestamp, null);
    }
}
