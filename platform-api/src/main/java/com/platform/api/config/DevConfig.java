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

    private static final String TENANT_1           = "tenant-1";
    private static final String SETTLEMENT_EX      = "SETTLEMENT_EXCEPTION";
    private static final String GROUP_OPS          = "group-ops";
    private static final String GROUP_SENIOR       = "group-senior";
    private static final String UNDER_REVIEW       = "UNDER_REVIEW";
    private static final String ESCALATED          = "ESCALATED";
    private static final String CLOSED             = "CLOSED";
    private static final String ANALYST            = "ANALYST";
    private static final String SUPERVISOR         = "SUPERVISOR";

    // Work item IDs (seed data)
    private static final String WI_001             = "wi-001";
    private static final String WI_003             = "wi-003";

    // Trade field keys (JSONB structure)
    private static final String F_TRADE            = "trade";
    private static final String F_VALUE_DATE       = "valueDate";
    private static final String F_NOTIONAL_AMOUNT  = "notionalAmount";
    private static final String F_AMOUNT           = "amount";
    private static final String F_CURRENCY         = "currency";
    private static final String F_COUNTERPARTY     = "counterparty";

    // Config map keys
    private static final String LAYOUT_TWO_COLUMN  = "TWO_COLUMN";
    private static final String STYLE_SECONDARY    = "SECONDARY";
    private static final String K_FIELD            = "field";
    private static final String K_LABEL            = "label";
    private static final String K_FORMATTER        = "formatter";
    private static final String K_TITLE            = "title";
    private static final String K_LAYOUT           = "layout";
    private static final String K_FIELDS           = "fields";
    private static final String K_TRANSITION       = "transition";
    private static final String K_STYLE            = "style";
    private static final String K_VISIBLE_STATES   = "visibleInStates";
    private static final String K_VISIBLE_ROLES    = "visibleRoles";

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
                    && SETTLEMENT_EX.equals(workflowType)) {
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
            "tenantId",     TENANT_1,
            "workflowType", SETTLEMENT_EX,
            "active",       true,
            "version",      1,
            "sections", List.of(
                Map.of(K_TITLE, "Trade Details", K_LAYOUT, LAYOUT_TWO_COLUMN, K_FIELDS, List.of(
                    Map.of(K_FIELD, "trade.ref",                     K_LABEL, "Trade Ref",   K_FORMATTER, "TEXT"),
                    Map.of(K_FIELD, "trade.valueDate",               K_LABEL, "Value Date",  K_FORMATTER, "DATE"),
                    Map.of(K_FIELD, "trade.notionalAmount.amount",   K_LABEL, "Notional",    K_FORMATTER, "CURRENCY"),
                    Map.of(K_FIELD, "trade.notionalAmount.currency", K_LABEL, "Currency",    K_FORMATTER, "TEXT"),
                    Map.of(K_FIELD, "status",                        K_LABEL, "Status",      K_FORMATTER, "BADGE"),
                    Map.of(K_FIELD, "priorityLevel",                 K_LABEL, "Priority",    K_FORMATTER, "BADGE")
                )),
                Map.of(K_TITLE, "Counterparty", K_LAYOUT, LAYOUT_TWO_COLUMN, K_FIELDS, List.of(
                    Map.of(K_FIELD, "counterparty.name", K_LABEL, "Name", K_FORMATTER, "TEXT"),
                    Map.of(K_FIELD, "counterparty.lei",  K_LABEL, "LEI",  K_FORMATTER, "TEXT")
                )),
                Map.of(K_TITLE, "Assignment", K_LAYOUT, LAYOUT_TWO_COLUMN, "collapsible", true, K_FIELDS, List.of(
                    Map.of(K_FIELD, "assignedGroup", K_LABEL, "Group",   K_FORMATTER, "TEXT"),
                    Map.of(K_FIELD, "source",        K_LABEL, "Source",  K_FORMATTER, "TEXT"),
                    Map.of(K_FIELD, "makerUserId",   K_LABEL, "Maker",   K_FORMATTER, "TEXT"),
                    Map.of(K_FIELD, "createdAt",     K_LABEL, "Created", K_FORMATTER, "DATETIME")
                ))
            ),
            "actions", List.of(
                Map.of(K_TRANSITION, "close-as-resolved",
                       K_LABEL, "Close as Resolved", K_STYLE, "PRIMARY",
                       K_VISIBLE_STATES, List.of(UNDER_REVIEW, ESCALATED),
                       K_VISIBLE_ROLES, List.of(ANALYST, SUPERVISOR),
                       "confirmationRequired", true,
                       "confirmationMessage", "Close this exception as resolved?",
                       "inputFields", List.of(
                           Map.of(K_FIELD, "resolution.reason", K_LABEL, "Reason",
                                  "inputType", "TEXTAREA", "required", true)
                       )),
                Map.of(K_TRANSITION, "escalate",
                       K_LABEL, "Escalate", K_STYLE, STYLE_SECONDARY,
                       K_VISIBLE_STATES, List.of(UNDER_REVIEW),
                       K_VISIBLE_ROLES, List.of(ANALYST, SUPERVISOR)),
                Map.of(K_TRANSITION, "assign-to-compliance",
                       K_LABEL, "Compliance Review", K_STYLE, STYLE_SECONDARY,
                       K_VISIBLE_STATES, List.of(UNDER_REVIEW, ESCALATED),
                       K_VISIBLE_ROLES, List.of(SUPERVISOR)),
                Map.of(K_TRANSITION, "return-to-review",
                       K_LABEL, "Return to Review", K_STYLE, STYLE_SECONDARY,
                       K_VISIBLE_STATES, List.of(ESCALATED),
                       K_VISIBLE_ROLES, List.of(SUPERVISOR))
            )
        );
    }

    // ── Seed data ─────────────────────────────────────────────────────────────

    private static List<WorkItem> seed() {
        Instant now = Instant.now();
        return List.of(
            item(WI_001, TENANT_1, SETTLEMENT_EX, SourceType.KAFKA,
                UNDER_REVIEW, GROUP_OPS, 750, "CRITICAL", now.minusSeconds(7200),
                Map.of(
                    F_TRADE, Map.of(
                        "ref", "TRD-20241015-001",
                        F_VALUE_DATE, "2024-10-17",
                        F_NOTIONAL_AMOUNT, Map.of(F_AMOUNT, "15000000.00", F_CURRENCY, "GBP")),
                    F_COUNTERPARTY, Map.of(
                        "name", "Barclays Capital",
                        "lei", "G5GSEF7VJP5I7OUK5573"))),

            item("wi-002", TENANT_1, SETTLEMENT_EX, SourceType.KAFKA,
                UNDER_REVIEW, GROUP_OPS, 400, "HIGH", now.minusSeconds(3600),
                Map.of(
                    F_TRADE, Map.of(
                        "ref", "TRD-20241015-002",
                        F_VALUE_DATE, "2024-10-18",
                        F_NOTIONAL_AMOUNT, Map.of(F_AMOUNT, "2500000.00", F_CURRENCY, "EUR")),
                    F_COUNTERPARTY, Map.of(
                        "name", "Deutsche Bank AG",
                        "lei", "7LTWFZYICNSX8D621K86"))),

            item(WI_003, TENANT_1, SETTLEMENT_EX, SourceType.DB_POLL,
                ESCALATED, GROUP_SENIOR, 850, "CRITICAL", now.minusSeconds(18000),
                Map.of(
                    F_TRADE, Map.of(
                        "ref", "TRD-20241014-087",
                        F_VALUE_DATE, "2024-10-15",
                        F_NOTIONAL_AMOUNT, Map.of(F_AMOUNT, "50000000.00", F_CURRENCY, "USD")),
                    F_COUNTERPARTY, Map.of(
                        "name", "JP Morgan Chase",
                        "lei", "8I5DZWZKVSZI1NUHU748"))),

            item("wi-004", TENANT_1, SETTLEMENT_EX, SourceType.FILE_UPLOAD,
                CLOSED, GROUP_OPS, 50, "LOW", now.minusSeconds(86400),
                Map.of(
                    F_TRADE, Map.of(
                        "ref", "TRD-20241013-044",
                        F_VALUE_DATE, "2024-10-14",
                        F_NOTIONAL_AMOUNT, Map.of(F_AMOUNT, "175000.00", F_CURRENCY, "GBP")),
                    F_COUNTERPARTY, Map.of(
                        "name", "HSBC Holdings",
                        "lei", "MLU0ZO3ML4LN2LL2TL39"))),

            item("wi-005", TENANT_1, SETTLEMENT_EX, SourceType.KAFKA,
                UNDER_REVIEW, GROUP_OPS, 300, "MEDIUM", now.minusSeconds(1800),
                Map.of(
                    F_TRADE, Map.of(
                        "ref", "TRD-20241015-031",
                        F_VALUE_DATE, "2024-10-20",
                        F_NOTIONAL_AMOUNT, Map.of(F_AMOUNT, "8750000.00", F_CURRENCY, "CHF")),
                    F_COUNTERPARTY, Map.of(
                        "name", "UBS Group AG",
                        "lei", "BFM8T61CT2L1QCEMIK50")))
        );
    }

    private static Map<String, List<AuditEntry>> seedAudit() {
        Instant base = Instant.now().minusSeconds(7200);
        return Map.of(
            storeKey(TENANT_1, WI_001), List.of(
                auditEntry(TENANT_1, WI_001, AuditEventType.INGESTION,
                    null, UNDER_REVIEW, base),
                auditEntry(TENANT_1, WI_001, AuditEventType.ASSIGNMENT,
                    UNDER_REVIEW, UNDER_REVIEW, base.plusSeconds(5))),
            storeKey(TENANT_1, WI_003), List.of(
                auditEntry(TENANT_1, WI_003, AuditEventType.INGESTION,
                    null, UNDER_REVIEW, base.minusSeconds(10800)),
                auditEntry(TENANT_1, WI_003, AuditEventType.STATE_TRANSITION,
                    UNDER_REVIEW, ESCALATED, base.minusSeconds(7200)),
                auditEntry(TENANT_1, WI_003, AuditEventType.SLA_WARNING,
                    ESCALATED, ESCALATED, base.minusSeconds(3600)))
        );
    }

    // ── Transition logic ──────────────────────────────────────────────────────

    private static String applyTransition(String currentStatus, String transitionName) {
        return switch (transitionName) {
            case "close-as-resolved"   -> CLOSED;
            case "escalate"            -> ESCALATED;
            case "return-to-review"    -> UNDER_REVIEW;
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
