--liquibase formatted sql

--changeset platform:008-source-connections
CREATE TABLE source_connections (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    connection_type VARCHAR(20)  NOT NULL,
    config          JSONB        NOT NULL DEFAULT '{}',
    credentials_ref VARCHAR(255),
    created_by      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX idx_source_connections_name ON source_connections (name);

--rollback DROP TABLE source_connections;

--changeset platform:008-source-connection-access
CREATE TABLE source_connection_access (
    id                   VARCHAR(36)  NOT NULL PRIMARY KEY,
    source_connection_id VARCHAR(36)  NOT NULL REFERENCES source_connections(id),
    tenant_id            VARCHAR(100) NOT NULL,
    granted_by           VARCHAR(255) NOT NULL,
    granted_at           TIMESTAMPTZ  NOT NULL,
    UNIQUE (source_connection_id, tenant_id)
);

CREATE INDEX idx_connection_access_tenant
    ON source_connection_access (tenant_id, source_connection_id);

--rollback DROP TABLE source_connection_access;
