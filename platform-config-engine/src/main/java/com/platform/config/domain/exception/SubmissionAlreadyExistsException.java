package com.platform.config.domain.exception;

public class SubmissionAlreadyExistsException extends RuntimeException {
    public SubmissionAlreadyExistsException(String workflowType) {
        super("A live submission already exists for workflow type: " + workflowType);
    }
}
