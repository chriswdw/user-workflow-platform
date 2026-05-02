package com.platform.config.domain.exception;

public class IncompleteSubmissionException extends RuntimeException {
    public IncompleteSubmissionException(String detail) {
        super("Submission is incomplete and cannot be submitted for approval: " + detail);
    }
}
