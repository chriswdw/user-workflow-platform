# Plan: Make Dev Fallback Config Opt-In, Not Opt-Out

## Context

`platform-api` review found `DevConfig` uses an opt-out profile guard instead of opt-in:

- `platform-api/src/main/java/com/platform/api/config/DevConfig.java:39` — `@Profile("!local")`.
  This means the in-memory fallback beans (fake seeded trades at `DevConfig.java:215-278`, a
  hand-rolled fake workflow state machine at `DevConfig.java:118-132,300-309`) are **candidates in
  every profile except `local`** — including `default` or a future `prod` profile.
- The only other guard is `@ConditionalOnMissingBean` (`DevConfig.java:96-157`) on `@Bean` methods
  inside a plain `@Configuration` (not `@AutoConfiguration`) class — Spring Boot's own guidance is
  that `@ConditionalOnMissingBean` ordering across user `@Configuration` classes is **not
  guaranteed**. If `DevConfig` is processed before `PostgresAdapterConfig`, you get either a
  `NoUniqueBeanDefinitionException` or unpredictable bean resolution instead of a clean
  "real adapter wins" story.
- `LocalDemoDataLoader.java:19` — `@Profile("local")` — sits in the same package and does this
  correctly (opt-in). That contrast is the evidence this was an oversight, not a deliberate
  choice.
- `DevConfig.java:33-37`'s own doc comment references "a real adapter (e.g. JPA + PostgreSQL)" —
  this repo forbids JPA/Hibernate outright (CLAUDE.md: "No JPA/Hibernate — ever"), so the comment
  is stale and misleading.
- SRP: `DevConfig` is 341 lines mixing bean wiring, ~130 lines of seed data, and a duplicate
  hand-rolled workflow engine with no maker-checker and no validation rules — a second,
  untested-against-drift implementation of behavior the real `WorkflowService` owns.

---

## Step 1 — Flip to opt-in

**`DevConfig.java:39`**
```java
@Profile("dev")
```
matching `LocalDemoDataLoader.java:19`'s pattern exactly.

## Step 2 — Confirm nothing currently relies on the opt-out behavior

Search for any test or CI config that runs with no active profile and expects `DevConfig`'s beans
to be present:
```bash
grep -rn "spring.profiles.active" platform-api/src/test platform-api/src/main
grep -rln "DevConfig" platform-api/src/test
```
If tests currently rely on `DevConfig` activating with no profile set, update them to explicitly
set `spring.profiles.active=dev` (via `@ActiveProfiles("dev")` or test properties) — this is the
correct fix, not a reason to keep the opt-out behavior.

## Step 3 — Fix the stale doc comment

**`DevConfig.java:33-37`** — update:
```java
/**
 * In-memory fallback implementations for all port interfaces, for local development without
 * a database. Active only under the "dev" profile. Each bean is also conditional on no other
 * implementation being present so that, if a real adapter happens to be registered in the same
 * context, it takes precedence — but the profile guard is the primary safety mechanism; do not
 * rely on @ConditionalOnMissingBean ordering alone across separate @Configuration classes.
 */
```

## Step 4 — Consider narrowing further (optional, discuss before doing)

Given the `@ConditionalOnMissingBean` ordering risk described above, consider whether `DevConfig`
should instead be split so the in-memory port beans and the seed-data/fake-workflow-engine logic
are separate `@Configuration` classes — the port beans are a legitimate "run without a database"
convenience, but the 100+ lines of fake trade data and the `applyTransition` switch statement
(`DevConfig.java:300-309`) are demo/UI-showcase concerns that arguably belong closer to
`LocalDemoDataLoader`'s pattern (SQL-seeded via `ApplicationRunner`, `@Profile("local")`) rather
than baked into in-memory port implementations under a differently-scoped `"dev"` profile. This is
a larger refactor — get agreement on scope before starting; the profile fix in Steps 1-3 is the
must-do part of this plan.

## Step 5 — Verify

```bash
./gradlew :platform-api:test
./gradlew :platform-api:cucumber
```
Manually confirm: start the app with `spring.profiles.active=local` (no `dev`) and a real
Postgres configured — `DevConfig`'s beans must not be candidates at all now (profile mismatch),
so there's no ambiguity for Spring to resolve. Start again with `spring.profiles.active=dev` and
no datasource configured — `DevConfig`'s fallbacks should still work exactly as before for local
UI development without a database.
