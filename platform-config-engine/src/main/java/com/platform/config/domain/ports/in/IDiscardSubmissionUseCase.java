package com.platform.config.domain.ports.in;

public interface IDiscardSubmissionUseCase {
  void discard(String tenantId, String submissionId, String actorUserId, boolean isAdmin);
}
