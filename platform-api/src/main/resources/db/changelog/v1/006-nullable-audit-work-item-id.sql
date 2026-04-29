--liquibase formatted sql

--changeset platform:006-nullable-audit-work-item-id
-- DUPLICATE_INGESTION_DISCARDED audit entries have no associated work item id

ALTER TABLE audit_entries ALTER COLUMN work_item_id DROP NOT NULL;

--rollback ALTER TABLE audit_entries ALTER COLUMN work_item_id SET NOT NULL;
