package com.platform.config.domain.exception;

public class SubmissionNotFoundException extends RuntimeException {
    public SubmissionNotFoundException(String submissionId) {
        super("Submission not found: " + submissionId);
    }
}
