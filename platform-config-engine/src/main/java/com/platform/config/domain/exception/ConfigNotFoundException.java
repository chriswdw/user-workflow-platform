package com.platform.config.domain.exception;

public class ConfigNotFoundException extends RuntimeException {
    public ConfigNotFoundException(String message) {
        super(message);
    }
}
