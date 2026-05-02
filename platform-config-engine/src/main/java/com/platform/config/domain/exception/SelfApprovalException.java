package com.platform.config.domain.exception;

public class SelfApprovalException extends RuntimeException {
    public SelfApprovalException(String userId) {
        super("User " + userId + " cannot approve their own submission");
    }
}
