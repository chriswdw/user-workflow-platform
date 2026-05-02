package com.platform.config.domain.service;

import com.platform.config.domain.exception.IncompleteSubmissionException;
import com.platform.config.domain.exception.SelfApprovalException;
import com.platform.config.domain.exception.SubmissionAlreadyExistsException;
import com.platform.config.domain.exception.SubmissionNotFoundException;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.CreateSubmissionCommand;
import com.platform.config.domain.ports.in.ICreateWorkflowTypeSubmissionUseCase;
import com.platform.config.domain.ports.in.IGetSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviewSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviseSubmissionUseCase;
import com.platform.config.domain.ports.in.ISaveDraftUseCase;
import com.platform.config.domain.ports.in.ISubmitForApprovalUseCase;
import com.platform.config.domain.ports.out.IConfigDocumentWriter;
import com.platform.config.domain.ports.out.IWorkflowTypeSubmissionRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class WorkflowTypeSubmissionService
        implements ICreateWorkflowTypeSubmissionUseCase,
                   ISubmitForApprovalUseCase,
                   IReviewSubmissionUseCase,
                   ISaveDraftUseCase,
                   IReviseSubmissionUseCase,
                   IGetSubmissionUseCase {

    private static final Pattern WORKFLOW_TYPE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final String DISPLAY_NAME_DRAFT = "Draft";

    private final IWorkflowTypeSubmissionRepository repo;
    private final IConfigDocumentWriter configDocumentWriter;
    private final boolean makerCheckerEnabled;

    public WorkflowTypeSubmissionService(IWorkflowTypeSubmissionRepository repo,
                                          IConfigDocumentWriter configDocumentWriter,
                                          boolean makerCheckerEnabled) {
        this.repo = repo;
        this.configDocumentWriter = configDocumentWriter;
        this.makerCheckerEnabled = makerCheckerEnabled;
    }

    // ── ICreateWorkflowTypeSubmissionUseCase ──────────────────────────────────

    @Override
    public WorkflowTypeSubmission create(CreateSubmissionCommand command) {
        if (!WORKFLOW_TYPE_PATTERN.matcher(command.workflowType()).matches()) {
            throw new IllegalArgumentException(
                    "workflowType must match ^[A-Z][A-Z0-9_]*$, got: " + command.workflowType());
        }
        if (repo.existsByTenantAndWorkflowType(command.tenantId(), command.workflowType())) {
            throw new SubmissionAlreadyExistsException(command.workflowType());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        WorkflowTypeSubmission submission = new WorkflowTypeSubmission(
                UUID.randomUUID().toString(),
                command.tenantId(),
                command.workflowType(),
                command.displayName(),
                command.description(),
                SubmissionStatus.DRAFT,
                DISPLAY_NAME_DRAFT,
                command.draftConfigs(),
                command.actorUserId(),
                null, null, null, null,
                1, 1,
                now, now);

        WorkflowTypeSubmission saved = repo.save(submission);

        if (!makerCheckerEnabled) {
            return autoApprove(saved, command.actorUserId());
        }
        return saved;
    }

    // ── ISaveDraftUseCase ─────────────────────────────────────────────────────

    @Override
    public WorkflowTypeSubmission saveDraft(String tenantId, String submissionId,
                                             String actorUserId,
                                             DraftConfigs partialDraftConfigs,
                                             int currentStep) {
        WorkflowTypeSubmission submission = load(tenantId, submissionId);
        assertStatus(submission, SubmissionStatus.DRAFT, "saveDraft");
        assertOwner(submission, actorUserId, "saveDraft");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repo.save(new WorkflowTypeSubmission(
                submission.id(), submission.tenantId(), submission.workflowType(),
                submission.displayName(), submission.description(),
                submission.status(), submission.statusDisplayName(),
                partialDraftConfigs,
                submission.submittedBy(), submission.submittedAt(),
                submission.reviewedBy(), submission.reviewedAt(),
                submission.rejectionReason(),
                currentStep,
                submission.version() + 1,
                submission.createdAt(), now));
    }

    // ── ISubmitForApprovalUseCase ─────────────────────────────────────────────

    @Override
    public WorkflowTypeSubmission submit(String tenantId, String submissionId, String actorUserId) {
        WorkflowTypeSubmission submission = load(tenantId, submissionId);
        assertStatus(submission, SubmissionStatus.DRAFT, "submit");
        assertOwner(submission, actorUserId, "submit");

        if (!submission.draftConfigs().isComplete()) {
            throw new IncompleteSubmissionException(
                    "blotterConfig and detailViewConfig must both contain at least one entry");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repo.save(new WorkflowTypeSubmission(
                submission.id(), submission.tenantId(), submission.workflowType(),
                submission.displayName(), submission.description(),
                SubmissionStatus.PENDING_APPROVAL, "Pending Approval",
                submission.draftConfigs(),
                submission.submittedBy(), now,
                null, null, null,
                submission.currentStep(),
                submission.version() + 1,
                submission.createdAt(), now));
    }

    // ── IReviewSubmissionUseCase ──────────────────────────────────────────────

    @Override
    public WorkflowTypeSubmission approve(String tenantId, String submissionId, String reviewerUserId) {
        WorkflowTypeSubmission submission = load(tenantId, submissionId);
        assertStatus(submission, SubmissionStatus.PENDING_APPROVAL, "approve");
        assertNotSelfApproval(submission, reviewerUserId);

        publishConfigDocuments(submission);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repo.save(new WorkflowTypeSubmission(
                submission.id(), submission.tenantId(), submission.workflowType(),
                submission.displayName(), submission.description(),
                SubmissionStatus.APPROVED, "Approved",
                submission.draftConfigs(),
                submission.submittedBy(), submission.submittedAt(),
                reviewerUserId, now, null,
                submission.currentStep(),
                submission.version() + 1,
                submission.createdAt(), now));
    }

    @Override
    public WorkflowTypeSubmission reject(String tenantId, String submissionId,
                                          String reviewerUserId, String reason) {
        WorkflowTypeSubmission submission = load(tenantId, submissionId);
        assertStatus(submission, SubmissionStatus.PENDING_APPROVAL, "reject");
        assertNotSelfApproval(submission, reviewerUserId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repo.save(new WorkflowTypeSubmission(
                submission.id(), submission.tenantId(), submission.workflowType(),
                submission.displayName(), submission.description(),
                SubmissionStatus.REJECTED, "Rejected",
                submission.draftConfigs(),
                submission.submittedBy(), submission.submittedAt(),
                reviewerUserId, now, reason,
                submission.currentStep(),
                submission.version() + 1,
                submission.createdAt(), now));
    }

    // ── IReviseSubmissionUseCase ──────────────────────────────────────────────

    @Override
    public WorkflowTypeSubmission revise(String tenantId, String submissionId,
                                          String actorUserId, DraftConfigs updatedDraftConfigs) {
        WorkflowTypeSubmission submission = load(tenantId, submissionId);
        assertStatus(submission, SubmissionStatus.REJECTED, "revise");
        assertOwner(submission, actorUserId, "revise");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repo.save(new WorkflowTypeSubmission(
                submission.id(), submission.tenantId(), submission.workflowType(),
                submission.displayName(), submission.description(),
                SubmissionStatus.DRAFT, DISPLAY_NAME_DRAFT,
                updatedDraftConfigs,
                submission.submittedBy(), null,
                null, null, null,
                1,
                submission.version() + 1,
                submission.createdAt(), now));
    }

    // ── IGetSubmissionUseCase ─────────────────────────────────────────────────

    @Override
    public WorkflowTypeSubmission getById(String tenantId, String submissionId) {
        return load(tenantId, submissionId);
    }

    @Override
    public List<WorkflowTypeSubmission> getPendingForTenant(String tenantId) {
        return repo.findByTenantAndStatus(tenantId, SubmissionStatus.PENDING_APPROVAL);
    }

    @Override
    public List<WorkflowTypeSubmission> getDraftsForUser(String tenantId, String actorUserId) {
        return repo.findByTenantAndStatusAndUser(tenantId, SubmissionStatus.DRAFT, actorUserId);
    }

    @Override
    public List<WorkflowTypeSubmission> getRejectedForUser(String tenantId, String actorUserId) {
        return repo.findByTenantAndStatusAndUser(tenantId, SubmissionStatus.REJECTED, actorUserId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private WorkflowTypeSubmission load(String tenantId, String submissionId) {
        return repo.findById(tenantId, submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
    }

    private static void assertStatus(WorkflowTypeSubmission s, SubmissionStatus expected, String op) {
        if (s.status() != expected) {
            throw new IllegalStateException(
                    "Cannot " + op + " a submission in status " + s.status()
                    + "; expected " + expected);
        }
    }

    private static void assertOwner(WorkflowTypeSubmission s, String actorUserId, String op) {
        if (!s.submittedBy().equals(actorUserId)) {
            throw new IllegalStateException(
                    "User " + actorUserId + " is not the owner of submission " + s.id());
        }
    }

    private static void assertNotSelfApproval(WorkflowTypeSubmission s, String reviewerUserId) {
        if (s.submittedBy().equals(reviewerUserId)) {
            throw new SelfApprovalException(reviewerUserId);
        }
    }

    private void publishConfigDocuments(WorkflowTypeSubmission submission) {
        DraftConfigs dc = submission.draftConfigs();
        String tenantId = submission.tenantId();
        String workflowType = submission.workflowType();
        String version = String.valueOf(submission.version() + 1);

        List<ConfigDocument> docs = List.of(
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
                        dc.detailViewConfig(), version));

        configDocumentWriter.saveAll(docs);
    }

    private static ConfigDocument toDoc(String tenantId, String workflowType,
                                         ConfigType configType,
                                         Map<String, Object> content,
                                         String version) {
        return new ConfigDocument(
                UUID.randomUUID().toString(), tenantId, workflowType,
                configType, content, version, true);
    }

    private WorkflowTypeSubmission autoApprove(WorkflowTypeSubmission submission, String actorUserId) {
        // Maker-checker disabled: publish immediately without self-approval check.
        publishConfigDocuments(submission);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repo.save(new WorkflowTypeSubmission(
                submission.id(), submission.tenantId(), submission.workflowType(),
                submission.displayName(), submission.description(),
                SubmissionStatus.APPROVED, "Approved",
                submission.draftConfigs(),
                submission.submittedBy(), now,
                actorUserId, now, null,
                submission.currentStep(),
                submission.version() + 1,
                submission.createdAt(), now));
    }
}
