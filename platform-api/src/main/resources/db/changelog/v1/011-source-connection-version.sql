--liquibase formatted sql

--changeset platform:011-source-connection-version
-- Optimistic locking for admin edits: a write must present the version it last read, so two admins
-- editing the same connection can no longer silently overwrite each other. Existing rows start at 1.
ALTER TABLE source_connections ADD COLUMN version INT NOT NULL DEFAULT 1;

--rollback ALTER TABLE source_connections DROP COLUMN version;
