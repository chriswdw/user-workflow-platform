# Schema Documentation

JSON Schema draft-2020-12 definitions for the user-workflow-platform domain. All schemas use `$id`, `$schema`, `title`, and `description` fields.

## Directory structure

```
schemas/
  core/                                 — Instance entities (one row per work item or event)
    tenant.schema.json                  — Tenant metadata, timezone, contacts; FK anchor for all tenantId refs
    work-item.schema.json               — Instance data only; no type-level metadata
    work-item-dependency.schema.json    — Blocking dependency relationships (many-to-1 fan-in)
    audit-entry.schema.json             — Immutable audit log; unmasked changedFields
  config/                               — Type-level config (same for all items of a workflow type)
    workflow-type-definition.schema.json — Type manifest; entry point for all config
    field-type-registry.schema.json      — Field types, classification, idempotency strategy, index hints
    user-role-config.schema.json         — Valid roles and permissions hierarchy per tenant
    resolution-group.schema.json         — Group ownership, visibility, SLA alert threshold
    routing-config.schema.json           — Routing rules + mandatory defaultGroup
    ingestion-source-config.schema.json  — Source connection + column/field mappings (Kafka/DB/file)
    workflow-config.schema.json          — State machine, transitions, priority scoring, SLA rules
    blotter-config.schema.json           — List view (ag-Grid columns, masking, quick filters)
    detail-view-config.schema.json       — Drill-through view (sections, editability, action buttons)
```

## Type/instance separation rule

**Type-level** (same for all work items of a workflow type): field classification, field types, SLA rules, states, transitions, routing rules, display config, priority scoring config. Lives in `config/` documents — never on WorkItem.

**Instance-level** (specific to one work item): current status, group assignment, fields payload, priority score, audit trail references, parent/dependency links. Lives on WorkItem only.

`WorkItem.fields` carries no display or classification properties. Display schemas reference fields by dot-notation path, resolved at runtime by `FieldPathResolver` in `platform-domain`.

## Schema relationships

```
WorkItem ──(workflowType)──► WorkflowTypeDefinition ──► field-type-registry
                                                     ──► workflow-config
                                                     ──► routing-config
                                                     ──► blotter-config
                                                     ──► detail-view-config
WorkItem ──(assignedGroup)──► ResolutionGroup
WorkItem ──(tenantId)──► Tenant
WorkItemDependency ──(blockingWorkItemId / dependentWorkItemId)──► WorkItem
AuditEntry ──(workItemId)──► WorkItem
IngestionSourceConfig ──(workflowType)──► WorkflowTypeDefinition  [1-to-many; source config references type]
```

## Canonical format and YAML acceptance policy

JSON is the canonical storage format. YAML is accepted as an authoring convenience and converted to JSON on load by `platform-config-engine`. The validated form stored in the database and used at runtime is always JSON.

## Versioning

- Every config document has a `version` (integer, monotonically increasing) and an `active` boolean.
- Exactly one active document of each config type must exist per `(tenantId, workflowType)`. `validateConfigs` enforces this.
- `WorkItem.configVersionId` stores the `WorkflowTypeDefinition.version` active at ingestion time, enabling routing and workflow engines to evaluate items under historically-correct rules.
- `platform-config-engine` loads active config by querying `(tenantId, workflowType, active: true)` — no coupling to a central version manifest.

## validateConfigs task

Run `./gradlew validateConfigs` to validate all JSON configs in the repository against their schemas and enforce cross-schema constraints:

- Every `routing-config.defaultGroup` references an active `ResolutionGroup`
- Every `routing-config.rules[].targetGroup` references an active `ResolutionGroup`
- Every `detail-view-config.actions[].transition` references a transition in the active `workflow-config`
- Every role name referenced in any schema matches a role declared in `user-role-config`
- Every blotter column with `filterable: true` has `searchable: true` in `field-type-registry`
- Exactly one active config document of each type per `(tenantId, workflowType)`
- Priority config: sum of group max scores ≤ `maxTotalScore × 1.2`; level coverage is gapless; TEMPORAL_PROXIMITY has BEYOND catch-all; NUMERIC_THRESHOLD has default catch-all; all groups have `rationale` and `owner`

## PostgreSQL index generation

The `field-type-registry.fieldDeclarations[].searchable` and `pgIndexType` fields drive Liquibase migration generation for expression indexes on `WorkItem.fields` (JSONB column):

- `BTREE`: `CREATE INDEX ON work_items ((fields #>> '{path,to,field}'))`
- `BTREE_CAST_NUMERIC`: `CREATE INDEX ON work_items (((fields #>> '{path,to,amount}')::numeric))`
- `GIN`: `CREATE INDEX USING GIN ON work_items (fields jsonb_path_ops)`

A full GIN index on the `fields` column handles containment queries. Expression indexes handle equality, range, and sort operations on specific high-cardinality paths. No denormalisation (`searchIndex` sub-document) is needed.

## simulatePriority task

Run `./gradlew simulatePriority --workflowType=X` before deploying priority config changes. This task:

1. Runs the scoring engine against the last N work items of the type (configurable sample size)
2. Outputs a score histogram and % per priority level
3. Produces a diff report: items changing level vs their current level
4. **Fails the build** if projected CRITICAL% exceeds `priorityConfig.saturationAlertThreshold`

This task is mandatory before deploying changes to `workflow-config.priorityConfig`.
