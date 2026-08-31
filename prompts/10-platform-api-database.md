# Prompt 10 — platform-api Database Layer

## Goal
Create all 9 Liquibase migration scripts and all 9 JDBC repository implementations for `platform-api`. This is the infrastructure layer — no domain logic, only SQL and mapping.

## Liquibase migrations

### File structure
```
platform-api/src/main/resources/db/changelog/
  db.changelog-master.yaml
  v1/
    001-create-work-items.sql
    002-create-audit-entries.sql
    003-create-config-documents.sql
    004-seed-config-documents.sql
    005-seed-ingestion-config.sql
    006-nullable-audit-work-item-id.sql
    007-workflow-type-submissions.sql
    008-source-connections.sql
    009-source-connection-db-poll-rename.sql
```

### `db.changelog-master.yaml`
```yaml
databaseChangeLog:
  - include:
      file: db/changelog/v1/001-create-work-items.sql
      relativeToChangelogFile: false
  - include:
      file: db/changelog/v1/002-create-audit-entries.sql
      relativeToChangelogFile: false
  - include:
      file: db/changelog/v1/003-create-config-documents.sql
      relativeToChangelogFile: false
  - include:
      file: db/changelog/v1/004-seed-config-documents.sql
      relativeToChangelogFile: false
  - include:
      file: db/changelog/v1/005-seed-ingestion-config.sql
      relativeToChangelogFile: false
  - include:
      file: db/changelog/v1/006-nullable-audit-work-item-id.sql
      relativeToChangelogFile: false
  - include:
      file: db/changelog/v1/007-workflow-type-submissions.sql
      relativeToChangelogFile: false
  - include:
      file: db/changelog/v1/008-source-connections.sql
      relativeToChangelogFile: false
  - include:
      file: db/changelog/v1/009-source-connection-db-poll-rename.sql
      relativeToChangelogFile: false
```

### `001-create-work-items.sql`
```sql
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
CREATE INDEX idx_work_items_tenant_workflow ON work_items (tenant_id, workflow_type);
CREATE UNIQUE INDEX idx_work_items_idempotency ON work_items (tenant_id, workflow_type, idempotency_key);
CREATE INDEX idx_work_items_fields_gin ON work_items USING GIN (fields jsonb_path_ops);
--rollback DROP TABLE work_items;
```

### `002-create-audit-entries.sql`
```sql
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
CREATE INDEX idx_audit_entries_work_item ON audit_entries (tenant_id, work_item_id, timestamp DESC);
--rollback DROP TABLE audit_entries;
```

### `003-create-config-documents.sql`
```sql
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
CREATE INDEX idx_config_documents_lookup ON config_documents (tenant_id, workflow_type, config_type, active);
--rollback DROP TABLE config_documents;
```

### `004-seed-config-documents.sql`
Seed two rows for SETTLEMENT_EXCEPTION tenant `demo`:
- `DETAIL_VIEW_CONFIG` — content with `sections: [{title:"Summary",fields:[{path:"trade.tradeId",label:"Trade ID"},{path:"trade.notionalAmount.amount",label:"Notional",format:"CURRENCY"}]}]`
- `WORKFLOW_CONFIG` — content with `states:[{name:"NEW",terminal:false,allowedRoles:["ANALYST"]}]` and `transitions:[{name:"assign",fromState:"NEW",toState:"IN_REVIEW",trigger:"MANUAL",allowedRoles:["ANALYST"],requiresMakerChecker:false}]`

### `005-seed-ingestion-config.sql`
Seed one `INGESTION_SOURCE_CONFIG` row for SETTLEMENT_EXCEPTION with a sample Kafka topic JSON.

### `006-nullable-audit-work-item-id.sql`
```sql
--liquibase formatted sql
--changeset platform:006-nullable-audit-work-item-id
ALTER TABLE audit_entries ALTER COLUMN work_item_id DROP NOT NULL;
--rollback ALTER TABLE audit_entries ALTER COLUMN work_item_id SET NOT NULL;
```

### `007-workflow-type-submissions.sql`
```sql
--liquibase formatted sql
--changeset platform:007-submission-statuses
CREATE TABLE submission_statuses (
    code VARCHAR(30) NOT NULL PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    description TEXT
);
INSERT INTO submission_statuses (code, display_name, description) VALUES
    ('DRAFT','Draft','Being configured by the submitter'),
    ('PENDING_APPROVAL','Pending Approval','Awaiting review by a second approver'),
    ('APPROVED','Approved','Configuration is live'),
    ('REJECTED','Rejected','Declined by the reviewer');
--rollback DROP TABLE submission_statuses;

--changeset platform:007-workflow-type-submissions
CREATE TABLE workflow_type_submissions (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    tenant_id        VARCHAR(100) NOT NULL,
    workflow_type    VARCHAR(100) NOT NULL,
    display_name     VARCHAR(255) NOT NULL,
    description      TEXT,
    status           VARCHAR(30)  NOT NULL DEFAULT 'DRAFT' REFERENCES submission_statuses(code),
    draft_configs    JSONB        NOT NULL DEFAULT '{}',
    submitted_by     VARCHAR(255) NOT NULL,
    submitted_at     TIMESTAMPTZ,
    reviewed_by      VARCHAR(255),
    reviewed_at      TIMESTAMPTZ,
    rejection_reason TEXT,
    current_step     INTEGER      NOT NULL DEFAULT 1,
    version          INTEGER      NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_submissions_tenant_status ON workflow_type_submissions (tenant_id, status);
CREATE INDEX idx_submissions_submitted_by ON workflow_type_submissions (tenant_id, submitted_by, status);
CREATE UNIQUE INDEX idx_submissions_tenant_workflow_type ON workflow_type_submissions (tenant_id, workflow_type)
    WHERE status NOT IN ('REJECTED');
--rollback DROP TABLE workflow_type_submissions;
```

### `008-source-connections.sql`
```sql
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
CREATE INDEX idx_connection_access_tenant ON source_connection_access (tenant_id, source_connection_id);
--rollback DROP TABLE source_connection_access;
```

### `009-source-connection-db-poll-rename.sql`
```sql
--liquibase formatted sql
--changeset platform:009-db-poll-rename
UPDATE source_connections SET connection_type = 'DB_POLL' WHERE connection_type = 'DB';
--rollback UPDATE source_connections SET connection_type = 'DB' WHERE connection_type = 'DB_POLL';

--changeset platform:009-jsonb-type-backfill
UPDATE source_connections SET config = config || jsonb_build_object('type', connection_type)
WHERE config -> 'type' IS NULL;
--rollback UPDATE source_connections SET config = config - 'type' WHERE true;
```

## JDBC Repositories

All repositories live in `platform-api/src/main/java/com/platform/api/adapter/out/postgres/`.
All constructors take `(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper)` unless noted.

### Key patterns shared by all repositories

**JSONB write**: `CAST(:param AS jsonb)` in SQL; `objectMapper.writeValueAsString(obj)` for the param value.

**JSONB read**: `objectMapper.readValue(rs.getString(col), new TypeReference<>(){})` or domain record type.

**Timestamps write**: `OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)` — pass `OffsetDateTime` to JDBC, never raw `Instant`.

**Timestamps read**: `rs.getObject(col, OffsetDateTime.class).toInstant()` for `Instant` fields; `rs.getObject(col, OffsetDateTime.class)` for `OffsetDateTime` fields.

**Optimistic locking**: `WHERE id = :id AND version = :version` in UPDATE; rowCount=0 → `throw new OptimisticLockingFailureException(...)`.

**Tenant isolation**: every query filters by `tenant_id`.

---

### `WorkItemJdbcRepository.java`
Implements `IFindWorkItemPort`, `IListWorkItemsPort`, `IWorkItemRepository`.
- `findById(tenantId, workItemId)` — `SELECT * FROM work_items WHERE id=:id AND tenant_id=:tenantId`
- `findByTenantAndWorkflowType(tenantId, workflowType)` — ORDER BY `priority_score DESC NULLS LAST, created_at DESC`
- `save(workItem)` — UPDATE all mutable columns, version+1 WHERE version=:version; throw if rowCount=0
- `mapRow` — map all 21 fields; handle nullable `priority_score` as `Integer`

### `AuditEntryJdbcRepository.java`
Implements `IAuditEntryRepository` (which extends `IAuditRepository`).
- `save(entry)` — INSERT with `ON CONFLICT (id) DO NOTHING` (idempotent by id)
- `findByWorkItem(tenantId, workItemId)` — filter by both, ORDER BY timestamp ASC
- `findByQuery(query)` — dynamic WHERE clause; apply `page`/`pageSize` with `LIMIT`/`OFFSET`
- `mapRow` — deserialise `changed_fields` as `List<AuditEntry.ChangedField>` via Jackson

### `ConfigDocumentJdbcRepository.java`
Implements `IConfigDocumentRepository`.
- `findActive(tenantId, workflowType, configType)` — `WHERE ... AND active=true ORDER BY version DESC LIMIT 1`
- `findAllActive(tenantId, workflowType)` — `WHERE ... AND active=true`

### `ConfigDocumentJdbcWriter.java`
Implements `IConfigDocumentWriter`.
- `saveAll(documents)` — batch INSERT with `ON CONFLICT (id) DO UPDATE SET content=EXCLUDED.content, version=EXCLUDED.version, active=EXCLUDED.active`

### `WorkflowConfigJdbcRepository.java`
Implements `IWorkflowConfigRepository`.
- `findActiveByTenantAndWorkflowType(tenantId, workflowType)` — JOIN `config_documents` where `config_type='WORKFLOW_CONFIG'` AND `active=true`; deserialise JSON to `WorkflowConfig` via Jackson

### `WorkflowTypeSubmissionJdbcRepository.java`
Implements `IWorkflowTypeSubmissionRepository` (from config-engine).
- `save(submission)` — INSERT with `ON CONFLICT (id) DO UPDATE ... WHERE version = EXCLUDED.version - 1`; rowCount=0 → `OptimisticLockingFailureException`
- `findById(tenantId, id)` — JOIN `submission_statuses` to get `status_display_name`
- `findByTenantAndStatus(tenantId, status)` — filter by tenant + status, ORDER BY `updated_at DESC`
- `findByTenantAndStatusAndUser(tenantId, status, userId)` — adds `submitted_by=:userId`
- `deleteById(tenantId, id)` — `DELETE FROM workflow_type_submissions WHERE tenant_id=:tenantId AND id=:id`
- `existsByTenantAndWorkflowType(tenantId, workflowType)` — `COUNT(*) > 0 WHERE status NOT IN ('REJECTED')`
- `mapRow` — deserialise `draft_configs` using `objectMapper.readerFor(DraftConfigs.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json)` (DraftConfigs has a computed `isComplete()` that Jackson may serialise)

Implement `IWorkflowTypeSubmissionRepository` methods as follows (note the interface has method names from Prompt 07 — reconcile with actual SQL methods above if names differ slightly).

### `SourceConnectionJdbcRepository.java`
Implements `ISourceConnectionRepository`.
- `save(connection)` — INSERT with `ON CONFLICT (id) DO UPDATE`
- `findById(id)` — SELECT + JOIN `source_connection_access` to check existence; return full `SourceConnection`
- `findByTenantId(tenantId)` — JOIN `source_connection_access` on `tenant_id`
- `grantAccess(connectionId, tenantId, grantedBy)` — INSERT into `source_connection_access` with `ON CONFLICT DO NOTHING`
- `revokeAccess(connectionId, tenantId)` — DELETE from `source_connection_access`
- `mapRow` — deserialise `config` JSONB to the right `ConnectionConfig` subtype by reading the `type` field first

For `ConnectionConfig` deserialisation: read `config` JSON, parse the `type` field, then construct `KafkaConfig`/`DbPollConfig`/`FileShareConfig`.

### `IngestionConfigJdbcRepository.java`
Implements `IIngestionConfigRepository` (from platform-ingestion).
- `findByTenantAndWorkflowType` — query `config_documents` where `config_type='INGESTION_SOURCE_CONFIG'` AND `active=true`; deserialise content to `IngestionConfig` via Jackson

### `IngestionWorkItemJdbcRepository.java`
Implements `IIngestionWorkItemRepository`.
- `insert(workItem)` — INSERT INTO `work_items` with all columns; no optimistic locking needed (new row)

### `IdempotencyKeyJdbcRepository.java`
Implements `IIdempotencyKeyRepository`.
- `exists(tenantId, workflowType, key)` — query `work_items` for matching `idempotency_key` (the idempotency key is the work item idempotency key — this avoids a separate table)
- `save(tenantId, workflowType, key)` — no-op (the work item INSERT handles this atomically)

## Application properties

**`src/main/resources/application.properties`**
```properties
api.jwt.secret=dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLUhTMjU2
api.rate-limit.requests-per-minute=100
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
platform.ingestion.kafka.topic=work-items.ingest
platform.ingestion.kafka.dlq-topic=work-items.ingest.dlq
platform.ingestion.kafka.consumer-group=platform-ingestion-group
platform.ingestion.default-group=group-ops
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.prometheus.enabled=true
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
platform.config.maker-checker.enabled=true
```

**`src/main/resources/application-dev.properties`**
```properties
api.rate-limit.requests-per-minute=1000
logging.level.com.platform=DEBUG
```

## Verification
```bash
./gradlew :platform-api:build   # Compiles all repositories
```
Repository integration tests are added in Prompt 13.
