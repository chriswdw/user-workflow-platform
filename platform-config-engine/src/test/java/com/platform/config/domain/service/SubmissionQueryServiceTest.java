package com.platform.config.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.config.domain.exception.SubmissionNotFoundException;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.doubles.InMemoryWorkflowTypeSubmissionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SubmissionQueryService is the read-side use case behind the "get submission", "get pending", "get
 * my rejected", and "admin: get all drafts" endpoints. Driven directly via its input port with an
 * in-memory repository double, no Spring context.
 */
class SubmissionQueryServiceTest {

  private final InMemoryWorkflowTypeSubmissionRepository repo =
      new InMemoryWorkflowTypeSubmissionRepository();
  private final SubmissionQueryService service = new SubmissionQueryService(repo);

  @Test
  void getByIdThrowsSubmissionNotFoundExceptionWhenAbsent() {
    assertThatThrownBy(() -> service.getById("tenant-1", "missing-sub"))
        .isInstanceOf(SubmissionNotFoundException.class)
        .hasMessageContaining("missing-sub");
  }

  @Test
  void getByIdReturnsTheSubmissionWhenPresent() {
    repo.save(submission("sub-1", "tenant-1", SubmissionStatus.DRAFT, "user-1"));

    assertThat(service.getById("tenant-1", "sub-1").id()).isEqualTo("sub-1");
  }

  @Test
  void getRejectedForUserReturnsOnlyThatUsersRejectedSubmissions() {
    repo.save(submission("sub-rej", "tenant-1", SubmissionStatus.REJECTED, "user-1"));
    repo.save(submission("sub-other-rej", "tenant-1", SubmissionStatus.REJECTED, "user-2"));
    repo.save(submission("sub-draft", "tenant-1", SubmissionStatus.DRAFT, "user-1"));

    List<WorkflowTypeSubmission> result = service.getRejectedForUser("tenant-1", "user-1");

    assertThat(result).extracting(WorkflowTypeSubmission::id).containsExactly("sub-rej");
  }

  @Test
  void getAllDraftsForTenantReturnsDraftsAcrossAllUsers() {
    repo.save(submission("sub-d1", "tenant-1", SubmissionStatus.DRAFT, "user-1"));
    repo.save(submission("sub-d2", "tenant-1", SubmissionStatus.DRAFT, "user-2"));
    repo.save(submission("sub-pending", "tenant-1", SubmissionStatus.PENDING_APPROVAL, "user-1"));

    List<WorkflowTypeSubmission> result = service.getAllDraftsForTenant("tenant-1");

    assertThat(result)
        .extracting(WorkflowTypeSubmission::id)
        .containsExactlyInAnyOrder("sub-d1", "sub-d2");
  }

  private static WorkflowTypeSubmission submission(
      String id, String tenantId, SubmissionStatus status, String userId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    DraftConfigs draftConfigs =
        new DraftConfigs(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    return new WorkflowTypeSubmission(
        id,
        tenantId,
        "SETTLEMENT_EXCEPTION",
        "Display",
        "Description",
        status,
        status.name(),
        draftConfigs,
        userId,
        null,
        null,
        null,
        null,
        1,
        1,
        now,
        now);
  }
}
