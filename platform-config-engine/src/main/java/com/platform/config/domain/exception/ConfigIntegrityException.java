package com.platform.config.domain.exception;

public class ConfigIntegrityException extends RuntimeException {
    public ConfigIntegrityException(String message) {
        super(message);
    }
}
