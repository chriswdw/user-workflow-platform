# Plan: Stop Mislabeling Data-Integrity Failures as Duplicate Ingestions

## Context

The review traced a real failure chain end-to-end across three modules:

1. **`platform-api/src/main/java/com/platform/api/adapter/out/postgres/IngestionWorkItemJdbcRepository.java:65-69`**
   ```java
   try {
       jdbc.update(sql, params);
   } catch (DataIntegrityViolationException e) {
       throw new DuplicateIdempotencyKeyException(w.idempotencyKey());
   }
   ```
   This catches **any** `DataIntegrityViolationException` on the `work_items` insert — a NOT NULL
   violation, FK violation, or CHECK constraint failure would all be mislabeled as the intended
   unique-index race on `(tenant_id, workflow_type, idempotency_key)`.

2. **`platform-ingestion/src/main/java/com/platform/ingestion/domain/service/IngestionService.java:161-169`**
   catches `DuplicateIdempotencyKeyException` and converts it to `IngestionResult.Duplicate`,
   writing a `DUPLICATE_INGESTION_DISCARDED` audit entry — for what might not be a duplicate at
   all.

3. **`platform-api/src/main/java/com/platform/api/adapter/in/kafka/WorkItemKafkaConsumer.java:48-50`**
   logs `IngestionResult.Duplicate` at **DEBUG** and returns normally — no retry, no DLQ, no alert.

Net effect: a genuine data-integrity bug (e.g. a malformed field mapping producing a null in a
NOT NULL column) is silently swallowed and mislabeled in the audit trail as "sent twice," which is
actively misleading for a system with financial audit obligations. Fix only needs to happen at
layer 1 — layers 2 and 3 are working correctly *given* an accurate signal from the repository.

---

## Step 1 — Identify the actual unique constraint name

Find the Liquibase changeset defining the unique index on
`(tenant_id, workflow_type, idempotency_key)` for `work_items` (referenced in
`IdempotencyKeyJdbcRepository.java`'s doc comment). Note its constraint name — Postgres exposes
this on constraint-violation exceptions and it's the reliable way to distinguish "this specific
unique index fired" from "some other constraint fired."

## Step 2 — Narrow the catch in the adapter

**`IngestionWorkItemJdbcRepository.java:65-69`** — inspect the SQL state / constraint name before
mapping to `DuplicateIdempotencyKeyException`:

```java
try {
    jdbc.update(sql, params);
} catch (DuplicateKeyException e) {
    // DuplicateKeyException (a DataIntegrityViolationException subtype) is Spring's translation
    // of a unique/PK violation specifically — narrower than the parent type, but still not
    // constraint-specific. Confirm which unique constraints exist on work_items; if there is
    // more than one, check e.getCause() (a PSQLException) for the constraint name via
    // ((PSQLException) e.getCause()).getServerErrorMessage().getConstraint() and only translate
    // to DuplicateIdempotencyKeyException when it matches the idempotency-key index name found
    // in Step 1. Anything else — including any other DataIntegrityViolationException — should
    // propagate unchanged so it surfaces as a real failure, not a fabricated "duplicate."
    if (isIdempotencyKeyViolation(e)) {
        throw new DuplicateIdempotencyKeyException(w.idempotencyKey());
    }
    throw e;
}
```

Extract the constraint-name check into a small private helper (`isIdempotencyKeyViolation`) —
per CLAUDE.md's Clean Code rule, if a block needs a comment to explain it, it should be a named
method instead.

## Step 3 — Verify

Add a unit test for `IngestionWorkItemJdbcRepository` (adapter integration test using
`embedded-postgres`, per CLAUDE.md's "Adapter integration gaps... should use embedded-postgres"
guidance) covering:
- Genuine idempotency-key collision → `save()` throws `DuplicateIdempotencyKeyException`.
- A different constraint violation (e.g. insert with a `null` `workflow_type` if that column is
  NOT NULL) → `save()` propagates the original `DataIntegrityViolationException` (or a more
  specific subtype), **not** `DuplicateIdempotencyKeyException`.

Add or extend a Cucumber scenario in `platform-ingestion`'s feature files covering the same two
paths at the `IIngestRecordUseCase` level, using the in-memory `IIngestionWorkItemRepository`
double in `testFixtures` — have the double support simulating both failure kinds so the scenario
can assert `IngestionResult.Rejected` (or whatever the correct non-duplicate outcome should be —
decide this explicitly; today there's no such path since the mislabeling hides it) is produced
for the non-duplicate case, distinct from `IngestionResult.Duplicate`.

```bash
./gradlew :platform-api:test
./gradlew :platform-ingestion:cucumber
./gradlew build cucumber
```

## Step 4 — Consider: should a genuine data-integrity failure raise an alert?

Once real failures are no longer masked, decide whether `WorkItemKafkaConsumer` should treat them
distinctly — e.g. still route to the DLQ (current default error-handler behavior for any uncaught
exception, per `KafkaIngestionConfig.java`) but at ERROR log level with a metric increment, so an
operator actually notices a spike instead of just accumulating DLQ messages silently. This ties
into `09-harden-dlq-producer-config.md` if you're doing both — coordinate rather than duplicating
work.
