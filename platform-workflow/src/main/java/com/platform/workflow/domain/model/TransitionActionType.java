package com.platform.workflow.domain.model;

public enum TransitionActionType {
    HTTP_CALL,
    KAFKA_EVENT,
    EMAIL_NOTIFICATION,
    REASSIGN_GROUP,
    CREATE_CHILD_WORKFLOW
}
