# Plan: Add Optimistic Locking to SourceConnection

## Context

CLAUDE.md: "Optimistic locking: enforced in SQL via `WHERE id = :id AND version = :version`;
`UPDATE` row-count of 0 throws `OptimisticLockingFailureException` — always." The review found one
aggregate that doesn't follow this:

- `platform-domain/src/main/java/com/platform/domain/model/SourceConnection.java` — no `version`
  field at all.
- `platform-api/src/main/java/com/platform/api/adapter/out/postgres/SourceConnectionJdbcRepository.java:35-59`
  — `save()` does a plain `ON CONFLICT (id) DO UPDATE SET ...` upsert with no version check and no
  row-count inspection. Two admins editing the same connection concurrently silently overwrite
  each other with no conflict signal.

Compare the two adapters that already do this correctly (use as the reference pattern):
- `platform-api/.../WorkItemJdbcRepository.java:77,96-100` — explicit
  `WHERE id = :id AND tenant_id = :tenantId AND version = :version`, throws
  `OptimisticLockingFailureException` on zero rows.
- `platform-api/.../WorkflowTypeSubmissionJdbcRepository.java:55,75-80` — same pattern via
  `ON CONFLICT ... WHERE workflow_type_submissions.version = EXCLUDED.version - 1`.

`SourceConnection` is admin-managed config that drives real ingestion routing (Kafka bootstrap
servers, DB-poll queries, file-share paths) — a lost update here is a genuine operational risk,
not just a theoretical one.

---

## Step 1 — Schema first (per CLAUDE.md's BDD/schema discipline)

Find the Liquibase changeset that created `source_connections` (likely under
`platform-api/src/main/resources/db/changelog/`) and add a new changeset:
```yaml
- changeSet:
    id: add-source-connections-version
    author: <you>
    changes:
      - addColumn:
          tableName: source_connections
          columns:
            - column:
                name: version
                type: int
                defaultValueNumeric: 1
                constraints:
                  nullable: false
```
Never edit the existing changeset in place — CLAUDE.md requires Liquibase changesets only, with
explicit rollback support; add a new one.

## Step 2 — Domain model

**`platform-domain/src/main/java/com/platform/domain/model/SourceConnection.java`** — add
`int version` as a record component (after `credentialsRef`, before `createdBy` — match ordering
convention used by `WorkflowTypeSubmission`, or place wherever makes the constructor call sites
least disruptive). This ripples through every call site that constructs a `SourceConnection` —
find them all:
```bash
grep -rln "new SourceConnection(" platform-api platform-config-engine
```
Expected hits: `SourceConnectionController.java` (`adminCreate` line ~41, `adminUpdate` line
~66), `SourceConnectionService.java` (`create` and `update` methods), `SourceConnectionJdbcRepository.mapRow`
(`:137-150`), plus any test fixtures/in-memory doubles in `testFixtures`.

## Step 3 — Service layer: enforce version semantics

**`platform-config-engine/.../domain/service/SourceConnectionService.java`**
- `create()`: initialize `version = 1`.
- `update()`: the incoming `connection.version()` must match what's currently persisted before
  applying the update — mirror how `WorkflowTypeSubmissionService`/`WorkflowService` (in
  `platform-workflow`) handle this, i.e. read-modify-write with the caller's expected version
  passed through to the repository, not silently incremented server-side without the caller's
  knowledge. Confirm `IManageSourceConnectionsUseCase.update()`'s signature carries a version the
  caller supplied (from `SourceConnectionRequest` → controller → service), so a stale-read client
  actually gets rejected rather than always winning because the service always re-fetches fresh.

## Step 4 — Repository: real optimistic locking

**`SourceConnectionJdbcRepository.java:35-59`** — replace the blind upsert with the
`WorkItemJdbcRepository` pattern: separate `INSERT` (for `create`, no version check needed — it's
a brand new row) from `UPDATE ... WHERE id = :id AND version = :version`, throwing
`OptimisticLockingFailureException` when the row count is zero on update:

```java
@Override
public SourceConnection save(SourceConnection c) {
    if (c.version() <= 1 /* or an explicit isNew flag from the service layer */) {
        return insert(c);
    }
    String sql = """
            UPDATE source_connections SET
                display_name    = :displayName,
                config          = CAST(:config AS jsonb),
                credentials_ref = :credentialsRef,
                version         = version + 1,
                updated_at      = :updatedAt
            WHERE id = :id AND version = :version
            """;
    // ... bind params, then:
    int rows = jdbc.update(sql, params);
    if (rows == 0) {
        throw new OptimisticLockingFailureException(
                "SourceConnection " + c.id() + " was modified concurrently (expected version " + c.version() + ")");
    }
    return /* new SourceConnection with version() + 1, matching WorkItemJdbcRepository.save()'s return pattern */;
}
```
Decide the new-vs-update branch condition based on whatever `SourceConnectionService.create()`
actually passes (e.g. an explicit `isNew` boolean is cleaner than inferring from `version <= 1` —
your call, but be explicit rather than inferring from a magic number).

## Step 5 — Wire the conflict into the REST layer

`GlobalExceptionHandler.java:47-50` already handles `OptimisticLockingFailureException` → 409
CONFLICT — confirm this covers `SourceConnection` updates too (it should, since it's a generic
handler), no change needed there, just verify with a test.

## Step 6 — Verify

Add a Cucumber scenario in `platform-config-engine/src/test/resources/features/config/` (or
wherever `SourceConnection` scenarios currently live) covering: concurrent update with a stale
version is rejected with a conflict; sequential updates with the correct version succeed and the
version increments. Run:
```bash
./gradlew :platform-config-engine:cucumber
./gradlew :platform-api:test
./gradlew build cucumber
```
