package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowTypeSubmissionJdbcRepositoryTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WorkflowTypeSubmissionJdbcRepository repository =
            new WorkflowTypeSubmissionJdbcRepository(jdbc, objectMapper);

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE workflow_type_submissions", Map.of());
    }

    @Test
    void save_newSubmission_roundtripsAllFields() {
        WorkflowTypeSubmission s = submission("sub-1", "tenant-A", "SETTLEMENT_EXCEPTION",
                SubmissionStatus.DRAFT, "Draft", "user-1", 1);

        WorkflowTypeSubmission saved = repository.save(s);
        Optional<WorkflowTypeSubmission> found = repository.findById("tenant-A", "sub-1");

        assertThat(found).isPresent();
        WorkflowTypeSubmission result = found.get();
        assertThat(result.id()).isEqualTo("sub-1");
        assertThat(result.tenantId()).isEqualTo("tenant-A");
        assertThat(result.workflowType()).isEqualTo("SETTLEMENT_EXCEPTION");
        assertThat(result.status()).isEqualTo(SubmissionStatus.DRAFT);
        assertThat(result.statusDisplayName()).isEqualTo("Draft");
        assertThat(result.submittedBy()).isEqualTo("user-1");
        assertThat(result.version()).isEqualTo(1);
        assertThat(saved).isEqualTo(s);
    }

    @Test
    void save_existingSubmission_updatesVersionAndFields() {
        repository.save(submission("sub-2", "tenant-A", "SETTLEMENT_EXCEPTION",
                SubmissionStatus.DRAFT, "Draft", "user-1", 1));

        WorkflowTypeSubmission updated = submission("sub-2", "tenant-A", "SETTLEMENT_EXCEPTION",
                SubmissionStatus.PENDING_APPROVAL, "Pending Approval", "user-1", 2);
        repository.save(updated);

        WorkflowTypeSubmission result = repository.findById("tenant-A", "sub-2").orElseThrow();
        assertThat(result.status()).isEqualTo(SubmissionStatus.PENDING_APPROVAL);
        assertThat(result.statusDisplayName()).isEqualTo("Pending Approval");
        assertThat(result.version()).isEqualTo(2);
    }

    @Test
    void save_staleVersion_throwsOptimisticLockingFailureException() {
        repository.save(submission("sub-3", "tenant-A", "SETTLEMENT_EXCEPTION",
                SubmissionStatus.DRAFT, "Draft", "user-1", 1));

        WorkflowTypeSubmission stale = submission("sub-3", "tenant-A", "SETTLEMENT_EXCEPTION",
                SubmissionStatus.PENDING_APPROVAL, "Pending Approval", "user-1", 2);
        repository.save(stale);

        // Attempt to update with the same version again (now stale)
        assertThatThrownBy(() -> repository.save(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void findByTenantAndStatus_returnsOnlyMatchingStatus() {
        repository.save(submission("s-draft", "tenant-A", "TYPE_A",
                SubmissionStatus.DRAFT, "Draft", "user-1", 1));
        repository.save(submission("s-pending", "tenant-A", "TYPE_B",
                SubmissionStatus.PENDING_APPROVAL, "Pending Approval", "user-1", 1));

        List<WorkflowTypeSubmission> drafts = repository.findByTenantAndStatus("tenant-A", SubmissionStatus.DRAFT);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).id()).isEqualTo("s-draft");
    }

    @Test
    void findByTenantAndStatusAndUser_returnsOnlyMatchingUser() {
        repository.save(submission("s-u1", "tenant-A", "TYPE_A",
                SubmissionStatus.DRAFT, "Draft", "user-1", 1));
        repository.save(submission("s-u2", "tenant-A", "TYPE_B",
                SubmissionStatus.DRAFT, "Draft", "user-2", 1));

        List<WorkflowTypeSubmission> result = repository.findByTenantAndStatusAndUser(
                "tenant-A", SubmissionStatus.DRAFT, "user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("s-u1");
    }

    @Test
    void existsByTenantAndWorkflowType_trueForDraftPendingApproved_falseForRejected() {
        repository.save(submission("s-rej", "tenant-A", "TYPE_REJ",
                SubmissionStatus.REJECTED, "Rejected", "user-1", 1));
        repository.save(submission("s-draft", "tenant-A", "TYPE_DRAFT",
                SubmissionStatus.DRAFT, "Draft", "user-1", 1));

        assertThat(repository.existsByTenantAndWorkflowType("tenant-A", "TYPE_REJ")).isFalse();
        assertThat(repository.existsByTenantAndWorkflowType("tenant-A", "TYPE_DRAFT")).isTrue();
        assertThat(repository.existsByTenantAndWorkflowType("tenant-A", "TYPE_MISSING")).isFalse();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static WorkflowTypeSubmission submission(String id, String tenantId, String workflowType,
                                                      SubmissionStatus status, String statusDisplayName,
                                                      String userId, int version) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DraftConfigs draftConfigs = new DraftConfigs(
                Map.of("key", "val"), Map.of(), Map.of(), Map.of(),
                Map.of("col", "name"), Map.of("section", "details"));
        return new WorkflowTypeSubmission(
                id, tenantId, workflowType,
                "Display " + workflowType, "Description",
                status, statusDisplayName,
                draftConfigs,
                userId, null, null, null, null,
                1, version,
                now, now);
    }
}
