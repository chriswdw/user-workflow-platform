# Plan: Replace the Hardcoded Group Assignment Stub with Real Routing

## Context

`platform-api` review found that the **production** ingestion wiring — not a dev fallback —
hardcodes group assignment:

**`platform-api/src/main/java/com/platform/api/config/IngestionAdapterConfig.java:40-45`**
```java
@Bean
IGroupAssignmentPort groupAssignmentPort(
        @Value("${platform.ingestion.default-group:group-ops}") String defaultGroup) {
    return (tenantId, workflowType, fields) ->
            new IGroupAssignmentPort.AssignmentResult(defaultGroup, true);
}
```

This bean is defined inside `@ConditionalOnProperty(name = "spring.datasource.url")` — i.e. the
real deployment path — and it ignores `tenantId`, `workflowType`, and `fields` entirely, always
returning the same group with `routedByDefault=true`. `platform-api/build.gradle.kts` doesn't
even declare a dependency on `:platform-routing`. Per CLAUDE.md, "All routing... logic must be
expressible in JSON/YAML config — no hardcoded business logic in code" — this is exactly the
violation that constraint exists to prevent. Every work item across every tenant and workflow
type currently lands in `group-ops` regardless of routing config.

This is a bigger piece of work than the others in this set — it's wiring a whole module in, not a
localized fix. Budget more time for it and expect to need `Plan` mode per CLAUDE.md's mandated
workflow before writing code.

---

## Step 1 — Understand what `platform-routing` already exposes

Before touching `platform-api`, read `platform-routing`'s module layout:
```bash
find platform-routing/src/main/java -name "*.java" | sort
```
It's documented as "Rule evaluator + group assignment; pure domain, no infrastructure" in
CLAUDE.md's module table. Find its input port(s) — likely something like
`IEvaluateRoutingRulesUseCase` or similar under `domain/ports/in/`. Confirm its signature accepts
`tenantId`, `workflowType`, and the mapped fields (or a comparable shape), and returns something
that maps cleanly to `IGroupAssignmentPort.AssignmentResult(groupId, routedByDefault)`.

## Step 2 — Follow CLAUDE.md's Plan → Schema → BDD sequence

This is a "non-trivial feature" per CLAUDE.md's Schema and BDD Discipline section — enter plan
mode, confirm:
- Whether `platform-routing`'s rule config already has a JSON Schema in `/schemas/` (it should,
  per module responsibilities) — if not, that's a prerequisite gap in `platform-routing` itself,
  out of scope for `platform-api` but worth flagging back.
- Draft Cucumber scenarios for `platform-api`'s ingestion path covering: a work item that matches
  a configured routing rule lands in the configured group; a work item matching no rule falls
  back to `platform.ingestion.default-group` with `routedByDefault=true` (preserving today's
  fallback behavior, just no longer as the *only* behavior); a work item where routing evaluation
  itself fails (bad rule config) — decide and document the failure mode (reject vs. fall back —
  don't let this be an unhandled exception either way).

## Step 3 — Add the module dependency

**`platform-api/build.gradle.kts`**
```kotlin
implementation(project(":platform-routing"))
```

## Step 4 — Wire the real adapter

**`IngestionAdapterConfig.java:40-45`** — replace the stub bean with one that delegates to
`platform-routing`'s input port, keeping `platform.ingestion.default-group` only as the
no-rules-matched fallback (not the only path):

```java
@Bean
IGroupAssignmentPort groupAssignmentPort(
        IEvaluateRoutingRulesUseCase routingUseCase,   // exact type name from platform-routing — confirm in Step 1
        @Value("${platform.ingestion.default-group:group-ops}") String defaultGroup) {
    return (tenantId, workflowType, fields) -> {
        var result = routingUseCase.evaluate(tenantId, workflowType, fields);
        return result.isPresent()
                ? new IGroupAssignmentPort.AssignmentResult(result.get().groupId(), false)
                : new IGroupAssignmentPort.AssignmentResult(defaultGroup, true);
    };
}
```
Adjust to whatever `platform-routing`'s actual port/return type looks like once you've read it in
Step 1 — the shape above is illustrative, not literal.

Also wire whatever config-loading bean `platform-routing`'s use case needs (likely a repository
reading routing rules from `config_documents`, similar to `WorkflowConfigJdbcRepository` — check
if `platform-routing` needs its own JDBC adapter here in `platform-api/adapter/out/postgres/` or
if it consumes an existing one).

## Step 5 — Verify RED then GREEN

```bash
./gradlew :platform-api:cucumber   # new scenarios must FAIL before Step 4's wiring
# ... implement Step 4 ...
./gradlew build cucumber           # must pass with no failures
```

## Step 6 — Metrics

Per CLAUDE.md's Observability section: "routing rule evaluation latency" is an explicitly
required metric. If `platform-routing`'s domain service doesn't already emit it via
`MeterRegistry`, add it there (not in `platform-api`) — this module should just be wiring, not
adding instrumentation logic.
