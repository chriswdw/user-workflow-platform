package com.platform.api.adapter.in.kafka;

public class IngestionRejectionException extends RuntimeException {

    public IngestionRejectionException(String reason) {
        super(reason);
    }
}
