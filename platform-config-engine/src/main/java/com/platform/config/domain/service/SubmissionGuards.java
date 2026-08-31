package com.platform.config.domain.service;

import com.platform.config.domain.exception.SelfApprovalException;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.out.IConfigDocumentWriter;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SubmissionGuards {

    private SubmissionGuards() {}

    static void assertStatus(WorkflowTypeSubmission s, SubmissionStatus expected, String op) {
        if (s.status() != expected) {
            throw new IllegalStateException(
                    "Cannot " + op + " a submission in status " + s.status()
                    + "; expected " + expected);
        }
    }

    static void assertOwner(WorkflowTypeSubmission s, String actorUserId, String op) {
        if (!s.submittedBy().equals(actorUserId)) {
            throw new IllegalStateException(
                    "User " + actorUserId + " is not the owner of submission " + s.id());
        }
    }

    static void assertNotSelfApproval(WorkflowTypeSubmission s, String reviewerUserId) {
        if (s.submittedBy().equals(reviewerUserId)) {
            throw new SelfApprovalException(reviewerUserId);
        }
    }

    static void publishConfigDocuments(WorkflowTypeSubmission submission,
                                        IConfigDocumentWriter writer) {
        DraftConfigs dc = submission.draftConfigs();
        String tenantId = submission.tenantId();
        String workflowType = submission.workflowType();
        String version = String.valueOf(submission.version() + 1);

        writer.saveAll(List.of(
                toDoc(tenantId, workflowType, ConfigType.WORKFLOW_TYPE_DEFINITION,
                        dc.workflowTypeDefinition(), version),
                toDoc(tenantId, workflowType, ConfigType.FIELD_TYPE_REGISTRY,
                        dc.fieldTypeRegistry(), version),
                toDoc(tenantId, workflowType, ConfigType.INGESTION_SOURCE_CONFIG,
                        dc.ingestionSourceConfig(), version),
                toDoc(tenantId, workflowType, ConfigType.WORKFLOW_CONFIG,
                        dc.workflowConfig(), version),
                toDoc(tenantId, workflowType, ConfigType.BLOTTER_CONFIG,
                        dc.blotterConfig(), version),
                toDoc(tenantId, workflowType, ConfigType.DETAIL_VIEW_CONFIG,
                        dc.detailViewConfig(), version)));
    }

    static AuditEntry submissionAuditEntry(WorkflowTypeSubmission s,
                                            AuditEventType eventType,
                                            String previousState,
                                            String newState,
                                            String actorUserId) {
        return new AuditEntry(
                UUID.randomUUID().toString(),
                s.tenantId(),
                s.id(),
                null,
                eventType,
                previousState,
                newState,
                eventType.name().toLowerCase(),
                List.of(new AuditEntry.ChangedField("status", previousState, newState)),
                actorUserId,
                null,
                Instant.now(),
                s.id() + ":" + eventType.name() + ":" + s.version());
    }

    private static ConfigDocument toDoc(String tenantId, String workflowType,
                                         ConfigType configType,
                                         Map<String, Object> content,
                                         String version) {
        return new ConfigDocument(
                UUID.randomUUID().toString(), tenantId, workflowType,
                configType, content, version, true);
    }
}
