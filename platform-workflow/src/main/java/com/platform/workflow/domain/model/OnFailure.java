package com.platform.workflow.domain.model;

public enum OnFailure {
    ROLLBACK_TRANSITION,
    CONTINUE,
    RETRY
}
