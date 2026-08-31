--liquibase formatted sql

--changeset platform:009-db-poll-rename
UPDATE source_connections SET connection_type = 'DB_POLL' WHERE connection_type = 'DB';
--rollback UPDATE source_connections SET connection_type = 'DB' WHERE connection_type = 'DB_POLL';

--changeset platform:009-jsonb-type-backfill
UPDATE source_connections
SET config = config || jsonb_build_object('type', connection_type)
WHERE config -> 'type' IS NULL;
--rollback UPDATE source_connections SET config = config - 'type' WHERE true;
