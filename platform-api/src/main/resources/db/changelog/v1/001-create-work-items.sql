--liquibase formatted sql

--changeset platform:001-work-items
CREATE TABLE work_items (
    id                          VARCHAR(36)  NOT NULL PRIMARY KEY,
    tenant_id                   VARCHAR(100) NOT NULL,
    workflow_type               VARCHAR(100) NOT NULL,
    correlation_id              VARCHAR(36),
    config_version_id           VARCHAR(36),
    source                      VARCHAR(20)  NOT NULL,
    source_ref                  VARCHAR(255),
    idempotency_key             VARCHAR(255) NOT NULL,
    status                      VARCHAR(100) NOT NULL,
    assigned_group              VARCHAR(100) NOT NULL,
    routed_by_default           BOOLEAN      NOT NULL DEFAULT FALSE,
    fields                      JSONB        NOT NULL DEFAULT '{}',
    priority_score              INTEGER,
    priority_level              VARCHAR(20),
    priority_last_calculated_at TIMESTAMPTZ,
    pending_checker_id          VARCHAR(255),
    pending_checker_transition  VARCHAR(255),
    version                     INTEGER      NOT NULL DEFAULT 1,
    maker_user_id               VARCHAR(255),
    created_at                  TIMESTAMPTZ  NOT NULL,
    updated_at                  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_work_items_tenant_workflow
    ON work_items (tenant_id, workflow_type);

CREATE UNIQUE INDEX idx_work_items_idempotency
    ON work_items (tenant_id, workflow_type, idempotency_key);

CREATE INDEX idx_work_items_fields_gin
    ON work_items USING GIN (fields jsonb_path_ops);

--rollback DROP TABLE work_items;
