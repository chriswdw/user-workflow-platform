package com.platform.ingestion.domain.service;

import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
import com.platform.domain.model.WorkItem;
import com.platform.ingestion.domain.model.FieldMapping;
import com.platform.ingestion.domain.model.IngestionConfig;
import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;
import com.platform.ingestion.domain.model.UnknownColumnPolicy;
import com.platform.ingestion.domain.ports.in.IIngestRecordUseCase;
import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;
import com.platform.ingestion.domain.ports.out.IIdempotencyKeyRepository;
import com.platform.ingestion.domain.ports.out.IIngestionAuditRepository;
import com.platform.ingestion.domain.ports.out.IIngestionConfigRepository;
import com.platform.ingestion.domain.ports.out.IIngestionWorkItemRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Domain service implementing the work item ingestion use case.
 * No framework dependencies — constructor-injected output ports only.
 *
 * Algorithm:
 * 1. Load ingestion config
 * 2. Map source fields → nested WorkItem.fields via FieldMapping
 * 3. Reject if any required source field is absent
 * 4. Reject or ignore unknown source fields per UnknownColumnPolicy
 * 5. Compute idempotency key (EXPLICIT_FIELD or COMPOSITE_HASH)
 * 6. If duplicate: write DUPLICATE_INGESTION_DISCARDED audit, return Duplicate
 * 7. Assign resolution group via routing port
 * 8. Build and save WorkItem; save idempotency key; write INGESTION audit
 */
public class IngestionService implements IIngestRecordUseCase {

    private final IIngestionConfigRepository configRepository;
    private final IIdempotencyKeyRepository idempotencyRepository;
    private final IIngestionWorkItemRepository workItemRepository;
    private final IIngestionAuditRepository auditRepository;
    private final IGroupAssignmentPort groupAssignmentPort;

    public IngestionService(IIngestionConfigRepository configRepository,
                             IIdempotencyKeyRepository idempotencyRepository,
                             IIngestionWorkItemRepository workItemRepository,
                             IIngestionAuditRepository auditRepository,
                             IGroupAssignmentPort groupAssignmentPort) {
        this.configRepository = configRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.workItemRepository = workItemRepository;
        this.auditRepository = auditRepository;
        this.groupAssignmentPort = groupAssignmentPort;
    }

    @Override
    public IngestionResult ingest(RawInboundRecord inboundRecord) {
        IngestionConfig config = configRepository
                .findByTenantAndWorkflowTypeAndSourceType(
                        inboundRecord.tenantId(), inboundRecord.workflowType(), inboundRecord.sourceType())
                .orElseThrow(() -> new IllegalStateException(
                        "No ingestion config for tenantId=" + inboundRecord.tenantId()
                        + ", workflowType=" + inboundRecord.workflowType()
                        + ", sourceType=" + inboundRecord.sourceType()));

        // Map fields and validate
        Set<String> declaredSourceFields = config.fieldMappings().stream()
                .map(FieldMapping::sourceField)
                .collect(Collectors.toSet());

        Map<String, Object> mappedFields = new HashMap<>();

        for (FieldMapping mapping : config.fieldMappings()) {
            String value = inboundRecord.rawFields().get(mapping.sourceField());
            if (value == null) {
                if (mapping.required()) {
                    return new IngestionResult.Rejected(
                            "Required field missing: " + mapping.sourceField());
                }
            } else {
                setNestedValue(mappedFields, mapping.targetField(), value);
            }
        }

        // Handle unknown columns
        for (String sourceField : inboundRecord.rawFields().keySet()) {
            if (!declaredSourceFields.contains(sourceField)) {
                if (config.unknownColumnPolicy() == UnknownColumnPolicy.REJECT) {
                    return new IngestionResult.Rejected("Unknown column: " + sourceField);
                }
                // IGNORE and WARN: skip silently (WARN is an observability concern, not domain)
            }
        }

        // Compute idempotency key
        String idempotencyKey = computeIdempotencyKey(inboundRecord, config);

        // Duplicate check
        if (idempotencyRepository.exists(inboundRecord.tenantId(), inboundRecord.workflowType(), idempotencyKey)) {
            auditRepository.save(auditEntry(
                    inboundRecord.tenantId(), inboundRecord.makerUserId(),
                    AuditEventType.DUPLICATE_INGESTION_DISCARDED, idempotencyKey,
                    null, null));
            return new IngestionResult.Duplicate(idempotencyKey);
        }

        // Assign group
        IGroupAssignmentPort.AssignmentResult assignment =
                groupAssignmentPort.assignGroup(inboundRecord.tenantId(), inboundRecord.workflowType(), mappedFields);

        // Build WorkItem
        String workItemId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        WorkItem workItem = new WorkItem(
                workItemId,
                inboundRecord.tenantId(),
                inboundRecord.workflowType(),
                correlationId,
                null,                        // configVersionId — set by config engine in production
                inboundRecord.sourceType(),
                inboundRecord.sourceRef(),
                idempotencyKey,
                config.initialState(),
                assignment.groupId(),
                assignment.routedByDefault(),
                mappedFields,
                null, null, null,             // priority fields — computed separately
                null, null,                   // pendingChecker fields
                1,
                inboundRecord.makerUserId(),
                Instant.now(),
                Instant.now()
        );

        // Persist
        idempotencyRepository.save(inboundRecord.tenantId(), inboundRecord.workflowType(), idempotencyKey);
        WorkItem saved = workItemRepository.save(workItem);
        auditRepository.save(auditEntry(
                saved.tenantId(), saved.makerUserId(),
                AuditEventType.INGESTION, idempotencyKey,
                saved.id(), saved.correlationId()));

        return new IngestionResult.Created(saved);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String computeIdempotencyKey(RawInboundRecord inboundRecord, IngestionConfig config) {
        return switch (config.idempotencyKeyStrategy()) {
            case EXPLICIT_FIELD -> {
                String value = inboundRecord.rawFields().get(config.idempotencyExplicitField());
                if (value == null) throw new IllegalStateException(
                        "Idempotency key field '" + config.idempotencyExplicitField() + "' is absent");
                yield value;
            }
            case COMPOSITE_HASH -> {
                List<String> values = new ArrayList<>();
                for (String field : config.idempotencyKeyFields()) {
                    values.add(inboundRecord.rawFields().getOrDefault(field, ""));
                }
                yield sha256(String.join("|", values));
            }
        };
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setNestedValue(Map<String, Object> map, String dotPath, Object value) {
        int dot = dotPath.indexOf('.');
        if (dot == -1) {
            map.put(dotPath, value);
        } else {
            String head = dotPath.substring(0, dot);
            String tail = dotPath.substring(dot + 1);
            map.computeIfAbsent(head, k -> new HashMap<>());
            setNestedValue((Map<String, Object>) map.get(head), tail, value);
        }
    }

    private static AuditEntry auditEntry(String tenantId, String actorUserId,
                                          AuditEventType eventType, String idempotencyKey,
                                          String workItemId, String correlationId) {
        return new AuditEntry(
                UUID.randomUUID().toString(),
                tenantId,
                workItemId,
                correlationId,
                eventType,
                null, null, null,
                List.of(),
                actorUserId,
                "SYSTEM",
                Instant.now(),
                idempotencyKey
        );
    }
}
