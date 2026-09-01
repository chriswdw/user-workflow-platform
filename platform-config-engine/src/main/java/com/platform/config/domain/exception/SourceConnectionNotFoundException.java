package com.platform.config.domain.exception;

public class SourceConnectionNotFoundException extends RuntimeException {
    public SourceConnectionNotFoundException(String connectionId) {
        super("Source connection not found: " + connectionId);
    }
}
