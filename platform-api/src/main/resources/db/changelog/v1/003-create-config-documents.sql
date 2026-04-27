--liquibase formatted sql

--changeset platform:003-config-documents
CREATE TABLE config_documents (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    tenant_id     VARCHAR(100) NOT NULL,
    workflow_type VARCHAR(100),
    config_type   VARCHAR(50)  NOT NULL,
    content       JSONB        NOT NULL,
    version       VARCHAR(50)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_config_documents_lookup
    ON config_documents (tenant_id, workflow_type, config_type, active);

--rollback DROP TABLE config_documents;
