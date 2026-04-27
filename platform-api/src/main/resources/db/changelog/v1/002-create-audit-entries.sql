--liquibase formatted sql

--changeset platform:002-audit-entries
CREATE TABLE audit_entries (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(100) NOT NULL,
    work_item_id    VARCHAR(36)  NOT NULL,
    correlation_id  VARCHAR(36),
    event_type      VARCHAR(50)  NOT NULL,
    previous_state  VARCHAR(100),
    new_state       VARCHAR(100),
    transition_name VARCHAR(100),
    changed_fields  JSONB        NOT NULL DEFAULT '[]',
    actor_user_id   VARCHAR(255),
    actor_role      VARCHAR(100),
    timestamp       TIMESTAMPTZ  NOT NULL,
    idempotency_key VARCHAR(255)
);

CREATE INDEX idx_audit_entries_work_item
    ON audit_entries (tenant_id, work_item_id, timestamp DESC);

--rollback DROP TABLE audit_entries;
