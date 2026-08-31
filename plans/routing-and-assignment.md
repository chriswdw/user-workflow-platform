# Routing & Assignment — Design Plan

## Context

The platform already ingests work items and stamps each with an `assignedGroup` UUID, but the routing rule model is a flat ordered list with a single condition tree and a priority integer (`platform-routing/.../domain/model/RoutingRule.java`). That doesn't express the user's required pattern: a default group for a broad field (e.g. `client-region = EMEA`), overridden by a narrower field (`client-country = UK`), overridden again by a more specific one (`clientId = EE Telecoms`).

Operationally, there is also no way for a group member to **claim** an item from a queue, and no way for a **supervisor** to reassign within their group or **transfer cross-group**. `WorkItem` has no `assignedUserId`, and `AuditEventType.ASSIGNMENT` is defined but unwired.

This plan adds:
1. A **weighted-specificity routing model** layered on top of the existing tree-condition rules — most specific match wins, with admin-defined per-field weights and a config-validated tie-prevention check.
2. A **reference-data table** abstraction (a new `ConfigType`) so supervisors can maintain the field-value → group mappings without engineering touching code; the table rows are the rule data, the column definitions + weights are the rule shape.
3. A **claim / assign / release** lifecycle on `WorkItem` driven by new input ports, with supervisor-scoped reassignment and cross-group transfer that re-enters routing.
4. Admin UI for rule-shape definition, supervisor UI for reference-data maintenance, and member UI for self-claim from the group queue.

The hexagonal layout, JSONB config storage, DraftConfigs submission flow, optimistic-locking SQL pattern, AuditEntry invariant, and frontend split-pane admin pattern are all reused as-is.

---

## 1. Domain Model Changes

### 1a. Routing — add weighted-specificity match policy

**`platform-routing/src/main/java/com/platform/routing/domain/model/`** — extend, don't replace:

- `RoutingConfig` gains `matchPolicy: MatchPolicy` (`PRIORITY_FIRST_MATCH` | `WEIGHTED_SPECIFICITY`) and `fieldWeights: List<FieldWeight>` (only meaningful for `WEIGHTED_SPECIFICITY`). Existing tenants default to `PRIORITY_FIRST_MATCH` so current behaviour is preserved.
- New record `FieldWeight(String fieldPath, int weight)` — `fieldPath` is dot-notation resolved via existing `FieldPathResolver`.
- New record `SpecificityRule(String id, String name, Map<String, JsonNode> fieldConstraints, String targetGroupId, boolean active)`. A constraint with value `null` or absent means "don't constrain on this field". Score = sum of `fieldWeights[fieldPath]` for every constraint that matches the work item.
- `RoutingRule` (the tree-condition shape) stays for `PRIORITY_FIRST_MATCH`.
- `RoutingResult` adds `matchedRuleId`, `matchedScore`, `routedByDefault` so audit can record *why* a group was chosen.

`RoutingService.route()` branches on `matchPolicy`. For `WEIGHTED_SPECIFICITY`:
1. For each active `SpecificityRule`, check every constraint matches the work item via `FieldPathResolver`. Skip if any constraint fails.
2. Compute score; track max-scoring rule.
3. If max > 0 → emit `RoutingResult(targetGroupId, matchedRuleId, score, routedByDefault=false)`.
4. Otherwise → `defaultGroupId` with `routedByDefault=true` (`alertOnDefault` already triggers the metric).

**Tie handling**: ties are rejected at `validateConfigs` time (cross-rule analysis flags any pair of rules whose constraint sets could yield the same score on overlapping inputs). At runtime, if a tie still occurs (e.g. data drift), log WARN, fall through to `defaultGroupId`, emit `routing.tie` counter — never pick arbitrarily.

### 1b. Reference-data tables — new `ConfigType`

The supervisor's maintenance surface IS the rule data. A reference-data table is a DMN-style sheet whose columns are the `fieldWeights` declared in the routing config; rows are `SpecificityRule` records.

- New `ConfigType.REFERENCE_DATA_TABLE` in `platform-config-engine/.../domain/model/ConfigType.java`.
- New schema `/schemas/config/reference-data-table.schema.json` referencing the routing config's `fieldWeights` for column validity.
- Cross-schema check in `validateConfigs`: every `SpecificityRule.targetGroupId` references an active `ResolutionGroup`; every `fieldConstraints` key appears in `fieldWeights`.

The routing config holds *only* the rule shape (`fieldWeights`, `matchPolicy`, `defaultGroupId`); the reference-data table holds the rows. This is what lets a supervisor edit mappings without an admin re-issuing the routing config.

### 1c. Assignment / claim

**`platform-domain/src/main/java/com/platform/domain/model/WorkItem.java`** — add field `String assignedUserId` (nullable). New `with*` methods: `withAssignment(userId)`, `withClearedAssignment()`. Routing populates `assignedGroup` only; `assignedUserId` is set only by a claim or supervisor assignment.

**`platform-workflow/.../domain/`** — new input ports (separate use cases, separate audit events):

- `IClaimWorkItemUseCase.claim(ClaimCommand)` — actor must be a member of `assignedGroup`; work item must have `assignedUserId == null`; optimistic lock on `version`; produce `AuditEntry(eventType=ASSIGNMENT)`.
- `IReleaseWorkItemUseCase.release(ReleaseCommand)` — actor must be current `assignedUserId`; clears it.
- `IAssignWorkItemUseCase.assign(AssignCommand)` — supervisor only; two sub-modes:
  - **in-group**: `targetUserId` must be a member of current `assignedGroup`. Updates `assignedUserId` only.
  - **cross-group transfer**: `targetGroupId` differs from current. Clears `assignedUserId`, sets new `assignedGroup`. By config flag `requiresMakerCheckerOnCrossGroup`, may set `pendingCheckerId` instead of applying immediately (reusing the existing maker-checker fields on `WorkItem`).
- Cross-group transfers DO NOT re-enter routing automatically — the supervisor explicitly chose the destination. There is a separate `IRerouteWorkItemUseCase` for when an admin/supervisor wants to send it back through the rule engine after data correction.

New output port: `IGroupMembershipRepository.findGroupsForUser(userId, tenantId)` and `isSupervisorOf(userId, groupId)`. Backed by the existing `RESOLUTION_GROUP` config documents; in-memory double in test source set.

---

## 2. REST Adapters

**`platform-api/src/main/java/com/platform/api/adapter/in/rest/WorkItemController.java`** — add:

- `POST /api/v1/work-items/{id}/claim` → `IClaimWorkItemUseCase`
- `POST /api/v1/work-items/{id}/release` → `IReleaseWorkItemUseCase`
- `POST /api/v1/work-items/{id}/assign` body `AssignWorkItemRequest{ targetUserId?, targetGroupId?, reason }` → `IAssignWorkItemUseCase`
- `POST /api/v1/work-items/{id}/reroute` → `IRerouteWorkItemUseCase` (admin/supervisor only)

New `RoutingConfigController` and `ReferenceDataController` follow the existing `ConfigController` pattern — read live + draft via the submission flow, never bypass.

---

## 3. Persistence (Liquibase)

New changesets in `platform-api/src/main/resources/db/changelog/v1/`:

- `010-work-item-assigned-user.sql` — `ALTER TABLE work_items ADD COLUMN assigned_user_id VARCHAR(64) NULL` + partial index `(tenant_id, assigned_group, status) WHERE assigned_user_id IS NULL` for queue list, and `(assigned_user_id, status)` for "my work" view.
- `011-reference-data-table-config-type.sql` — extend `config_documents.config_type` allowed values (CHECK or reference table) to include `REFERENCE_DATA_TABLE`.

Existing `WorkItemJdbcRepository` `UPDATE … WHERE id = :id AND version = :version` extends to include `assigned_user_id`. Optimistic-locking pattern preserved verbatim.

---

## 4. Schemas

New / changed under `/schemas/config/`:

- `routing-config.schema.json` — add `matchPolicy`, `fieldWeights[]`. Keep `rules[]` (tree-condition) as `oneOf` alternative to no-rules-when-weighted.
- `reference-data-table.schema.json` — new. `{ tenantId, workflowType, name, version, rows: [{ id, fieldConstraints: {fieldPath: literal}, targetGroupId, active }] }`.
- `core/work-item.schema.json` — add `assignedUserId`.

`./gradlew validateConfigs` extended with three new cross-checks:
1. `SpecificityRule.fieldConstraints` keys ⊆ `RoutingConfig.fieldWeights[].fieldPath`.
2. `targetGroupId` references active `ResolutionGroup`.
3. Tie-prevention: no two rules share both the same constraint-key set and the same score.

`./gradlew generateTypes` regenerates frontend Zod schemas.

---

## 5. Frontend

Follow `AllDraftsAdminView.tsx` split-pane pattern.

New under `platform-frontend/src/components/admin/`:

- **`RoutingConfigAdminView.tsx`** (PLATFORM_ADMIN) — pick workflow type, edit `matchPolicy`, declare `fieldWeights` (path + integer), pick fallback group, save as draft via the existing submission flow. Reuses `wizardStore` builder pattern.
- **`ReferenceDataAdminView.tsx`** (SUPERVISOR + PLATFORM_ADMIN) — for a given workflow type, render an ag-Grid of `SpecificityRule` rows. Columns are derived from the live routing config's `fieldWeights`; cells are editable; group column is a dropdown of `ResolutionGroup`s. Inline "Add row" / "Disable row" actions. Save as draft → submit through the existing maker-checker flow if the workflow type requires it.
- **Group queue & claim** — extend `Blotter.tsx` with two new server-side views switchable from `App.tsx`:
  - `view = 'group-queue'` → items where `assignedGroup ∈ userGroups && assignedUserId == null`. Row action **Claim**.
  - `view = 'my-work'` → items where `assignedUserId == currentUser`. Row action **Release**.
- **Supervisor reassign** — when `role === 'SUPERVISOR'`, blotter rows in groups the user supervises gain **Reassign** (opens `AssignWorkItemModal` with member dropdown) and **Transfer to group** (group dropdown + reason).

Zod types regenerated; React Query hooks under `src/api/`:
- `useGroupQueue(groupIds)`, `useMyWork()`, `useClaimWorkItem()`, `useReleaseWorkItem()`, `useAssignWorkItem()`.
- `useRoutingConfig(workflowType)`, `useReferenceDataTable(workflowType)`, mutations for both.

All components follow the `interface XProps { readonly … }` + named-export convention from CLAUDE.md.

---

## 6. Observability

Per CLAUDE.md, observability is part of the feature — not an afterthought.

- Metrics: `routing.matches.total{policy, matched_rule_id, routed_by_default}`, `routing.score.distribution`, `routing.tie.count`, `assignment.claim.count`, `assignment.reassign.count{cross_group}`, `assignment.release.count`.
- Traces: claim / reassign / route all carry `workItemId`, `correlationId`, `matchedRuleId`.
- Logs: MDC `correlationId`, `workItemId`, `userId`, `tenantId` on every assignment event; no PII (group/user IDs only).
- Grafana dashboard `/observability/dashboards/routing-and-assignment.json`; alert on `routed_by_default` rate > threshold.

---

## 7. BDD-first Sequence (per CLAUDE.md "Schema and BDD Discipline")

Order of file creation, RED before GREEN:

1. Schemas: `routing-config.schema.json` (updated), `reference-data-table.schema.json` (new), `work-item.schema.json` (updated).
2. Cucumber features:
   - `platform-routing/src/test/resources/features/routing/weighted-specificity.feature` — the EMEA / UK / EE-Telecoms override example end-to-end, plus tie detection and no-match fallback.
   - `platform-workflow/src/test/resources/features/assignment/claim-release.feature` — claim, double-claim (optimistic-lock failure), release, claim by non-member rejected.
   - `platform-workflow/src/test/resources/features/assignment/reassignment.feature` — in-group reassign, cross-group transfer, cross-group with maker-checker, non-supervisor rejected.
3. In-memory port doubles in test source sets: extend `InMemoryRoutingConfigRepository`, add `InMemoryReferenceDataRepository`, `InMemoryGroupMembershipRepository`.
4. Step definition stubs (failing).
5. Run `./gradlew :platform-routing:cucumber :platform-workflow:cucumber` → verify RED.
6. Production code under `domain/service/` then `adapter/*/`.
7. `./gradlew build cucumber sonar` → GREEN; resolve all HIGH/CRITICAL.

---

## Critical Files

**Modify**
- `platform-routing/src/main/java/com/platform/routing/domain/model/RoutingConfig.java`
- `platform-routing/src/main/java/com/platform/routing/domain/model/RoutingRule.java` (or sibling new file)
- `platform-routing/src/main/java/com/platform/routing/domain/service/RoutingService.java`
- `platform-domain/src/main/java/com/platform/domain/model/WorkItem.java`
- `platform-workflow/src/main/java/com/platform/workflow/domain/service/WorkflowService.java`
- `platform-api/src/main/java/com/platform/api/adapter/in/rest/WorkItemController.java`
- `platform-api/src/main/java/com/platform/api/adapter/out/postgres/WorkItemJdbcRepository.java`
- `platform-config-engine/src/main/java/com/platform/config/domain/model/ConfigType.java`
- `schemas/config/routing-config.schema.json`
- `schemas/core/work-item.schema.json`
- `platform-frontend/src/App.tsx`
- `platform-frontend/src/components/blotter/Blotter.tsx`

**Create**
- `platform-routing/src/main/java/com/platform/routing/domain/model/SpecificityRule.java`, `FieldWeight.java`, `MatchPolicy.java`
- `platform-workflow/src/main/java/com/platform/workflow/domain/ports/in/IClaimWorkItemUseCase.java` (+ Release, Assign, Reroute)
- `platform-workflow/src/main/java/com/platform/workflow/domain/service/AssignmentService.java`
- `platform-workflow/src/main/java/com/platform/workflow/domain/ports/out/IGroupMembershipRepository.java`
- `platform-api/src/main/java/com/platform/api/adapter/in/rest/{Claim,Release,Assign,Reroute}WorkItemRequest.java`, `RoutingConfigController.java`, `ReferenceDataController.java`
- `platform-api/src/main/resources/db/changelog/v1/010-work-item-assigned-user.sql`, `011-reference-data-table-config-type.sql`
- `schemas/config/reference-data-table.schema.json`
- `platform-frontend/src/components/admin/RoutingConfigAdminView.tsx`, `ReferenceDataAdminView.tsx`, `AssignWorkItemModal.tsx`
- `platform-frontend/src/api/{useGroupQueue,useMyWork,useClaimWorkItem,useReleaseWorkItem,useAssignWorkItem,useRoutingConfig,useReferenceDataTable}.ts`
- `observability/dashboards/routing-and-assignment.json`, `observability/alerts/routing-and-assignment.yml`
- All Cucumber `.feature` files listed above + step definitions + in-memory doubles

**Reused as-is** (no changes)
- `FieldPathResolver`, `AuditEntry`, `IAuditRepository`, `ConfigDocument`, `WorkflowTypeSubmission` + `DraftConfigs`, optimistic-locking SQL pattern, ag-Grid blotter column-def pattern, `wizardStore` builder pattern.

---

## Verification

1. **Unit / domain**: `./gradlew :platform-routing:test :platform-workflow:test` — no Spring context.
2. **BDD**: `./gradlew cucumber` — weighted-specificity scenario produces EE-Desk for EE/UK/EMEA, UK-Ops for non-EE UK, EMEA-Ops for non-UK EMEA, Triage for unmatched. Claim/release/reassign scenarios all green.
3. **Adapter integration**: `embedded-postgres` test confirms `assigned_user_id` migration applies, optimistic lock failure on concurrent claim.
4. **Config validation**: `./gradlew validateConfigs` rejects a reference-data table with an unknown column, an unknown group, or a score tie.
5. **End-to-end UI**: start backend + `npm run dev`; as PLATFORM_ADMIN define routing config for workflow `TRADE_BREAK` with weights `region=1, country=2, clientId=4`, fallback `TRIAGE`; as SUPERVISOR add reference-data rows for EMEA, UK override, EE-Telecoms override; submit a sample work item and verify it lands in EE-Desk; claim it as a member; release; supervisor reassigns to another member; supervisor transfers cross-group to another group; verify audit log shows ASSIGNMENT, GROUP_REASSIGNMENT events with `matchedRuleId` and actor.
6. **Pre-PR**: `./gradlew build cucumber sonar` clean; `/pre-pr` skill green; coverage on all new production classes ≥ 80%.
