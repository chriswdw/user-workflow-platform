package com.platform.config.domain.ports.in;

import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.WorkflowTypeSubmission;

public interface IReviseSubmissionUseCase {
    WorkflowTypeSubmission revise(String tenantId, String submissionId, String actorUserId,
                                  DraftConfigs updatedDraftConfigs);
}
