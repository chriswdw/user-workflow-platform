package com.platform.config.domain.ports.in;

import com.platform.config.domain.model.WorkflowTypeSubmission;

public interface ICreateWorkflowTypeSubmissionUseCase {
    WorkflowTypeSubmission create(CreateSubmissionCommand command);
}
