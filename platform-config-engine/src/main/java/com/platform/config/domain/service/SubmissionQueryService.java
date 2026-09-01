package com.platform.config.domain.service;

import com.platform.config.domain.exception.SubmissionNotFoundException;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.IGetSubmissionUseCase;
import com.platform.config.domain.ports.out.IWorkflowTypeSubmissionRepository;
import java.util.List;

public class SubmissionQueryService implements IGetSubmissionUseCase {

  private final IWorkflowTypeSubmissionRepository repo;

  public SubmissionQueryService(IWorkflowTypeSubmissionRepository repo) {
    this.repo = repo;
  }

  @Override
  public WorkflowTypeSubmission getById(String tenantId, String submissionId) {
    return repo.findById(tenantId, submissionId)
        .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
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

  @Override
  public List<WorkflowTypeSubmission> getAllDraftsForTenant(String tenantId) {
    return repo.findByTenantAndStatus(tenantId, SubmissionStatus.DRAFT);
  }
}
