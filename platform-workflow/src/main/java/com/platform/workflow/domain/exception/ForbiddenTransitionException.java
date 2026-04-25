package com.platform.workflow.domain.exception;

public class ForbiddenTransitionException extends RuntimeException {
    public ForbiddenTransitionException(String message) {
        super(message);
    }
}
