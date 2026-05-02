# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Configuration-driven workflow platform for financial services. Work items ingested from Kafka, DB polling, or file upload are routed to resolution groups and managed via a configurable web blotter. **All routing, workflow, and display logic must be expressible in JSON/YAML config — no hardcoded business logic in code.**

## Commands

```bash
./gradlew build                                                  # Full build + unit tests
./gradlew cucumber                                               # All BDD feature tests
./gradlew :platform-routing:test                                 # Single module
./gradlew validateConfigs                                        # Validate JSON configs against schemas + cross-schema constraints
./gradlew generateTypes                                          # Generate TypeScript types from schemas
./gradlew simulatePriority --workflowType=X                     # Simulate priority score impact before deploying priorityConfig changes
docker compose -f docker/observability/docker-compose.yml up    # Full Grafana stack (optional — not required for dev or tests)
./gradlew sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=<token> \
  -Dsonar.projectKey=user-workflow-platform                      # Static analysis — run before every PR; resolve all HIGH and CRITICAL issues
```

**Developer environment**: Tests use `io.zonky.test:embedded-postgres` (real PostgreSQL binary in-JVM, no Docker) and `@EmbeddedKafka` from `spring-kafka-test`. No Docker required for running tests. **H2/HSQL are explicitly excluded** — they do not support PostgreSQL's JSONB operators (`@>`, `#>>`) or GIN indexes, making them unsuitable for validating the core data access pattern.

For local dev run: install PostgreSQL locally (no container required) and configure `spring.datasource.url` in `application-local.yml`.

## Agent Tool Usage

Prefer direct tools (Read, Edit, Bash, grep) over spawning sub-agents. Only use the Agent tool when:
- Exploration requires searching many locations across the codebase (subagent_type=Explore)
- Two or more tasks are genuinely independent and can run in parallel

Never spawn a sub-agent for a single file read, a targeted grep, or a simple code edit.

## Architecture — Hexagonal (Ports & Adapters)

> This is the hardest constraint to maintain and the most important. The domain core has zero framework dependencies. Violations break testability and defeat the entire design.

Every backend module follows this layout:

```
module/src/main/java/.../
  domain/
    model/       — Java records and sealed interfaces; no framework imports
    ports/in/    — input port interfaces (use cases the domain exposes)
    ports/out/   — output port interfaces (repos, messaging, external APIs)
    service/     — domain services implementing input ports; injected with output ports
  adapter/
    in/rest/     — Spring MVC controllers; call input ports only
    in/kafka/    — Kafka listeners; call input ports only
    in/file/     — file upload handlers; call input ports only
    out/postgres/ — implements repository output ports via NamedParameterJdbcTemplate + JSONB (see note below)
    out/kafka/   — implements messaging output ports via Spring Kafka
    out/http/    — implements external API output ports via WebClient
  config/        — Spring @Configuration and @Bean wiring; all DI lives here
```

**No JPA/Hibernate — ever.** All database access uses `NamedParameterJdbcTemplate` with explicit SQL. Hibernate is excluded because its session/proxy model, lazy-loading surprises, and N+1 behaviour are incompatible with the performance and auditability requirements of a financial services platform at scale. `spring-boot-starter-data-jpa` must not be added to any module. JSONB columns are written using `CAST(:param AS jsonb)` in SQL — no PGobject compile-time dependency. Timestamp parameters must be `OffsetDateTime` (not `java.time.Instant`) because the PostgreSQL JDBC driver cannot infer the SQL type for `Instant`.

**Rules enforced on every PR:**
- Domain services declare constructor dependencies on output port interfaces only — never on concrete adapters
- All wiring in `config/` — no `@Autowired` in domain or adapter classes
- Every port must have an in-memory test double in the test source set
- Domain services tested with no Spring context; adapters tested in isolation against port contracts
- Cucumber scenarios drive domain services directly via input ports using in-memory doubles

## Gradle Modules

| Module | Responsibility |
|---|---|
| `platform-domain` | Shared model: WorkItem, WorkflowState, RoutingRule, AuditEntry |
| `platform-config-engine` | Loads, validates, hot-reloads JSON config from PostgreSQL |
| `platform-ingestion` | Kafka consumer, DB polling, file upload — normalise to WorkItem |
| `platform-routing` | Rule evaluator + group assignment; pure domain, no infrastructure |
| `platform-workflow` | State machine, transition execution, action dispatcher |
| `platform-audit` | Immutable audit log; domain enforces append-only |
| `platform-api` | Spring MVC REST, JWT security, rate limiting, API versioning |
| `platform-observability` | Shared Micrometer config, structured logging, trace propagation |
| `platform-frontend` | React/Vite application |

## Tech Stack

- **Backend**: Spring Boot 3.x, Java 21, Gradle 8.x Kotlin DSL, `libs.versions.toml` for all versions
- **Database**: PostgreSQL with JSONB. `WorkItem.fields` stored as JSONB with GIN index for containment queries; expression indexes on searchable paths declared in `field-type-registry`. Schema changes via **Liquibase** only (chosen over Flyway for explicit rollback changesets — critical for emergency deployments in financial services).
- **Messaging**: Apache Kafka (Spring Kafka)
- **Frontend**: React + TypeScript, ag-Grid (blotter), React Hook Form (config editors), Zustand, React Query, Vite
- **Security**: Spring Security + JWT, RBAC, field-level encryption
- **Testing**: JUnit 5 + Cucumber (backend) with `io.zonky.test:embedded-postgres` + `@EmbeddedKafka` — no Docker required; Jest + React Testing Library + Cucumber.js (frontend)
- **Observability**: Prometheus → Mimir, Grafana, Loki, Tempo, Pyroscope — all via Micrometer/OTLP

## Financial Services Constraints

> These are regulatory and audit requirements, not preferences. Every item is mandatory.

- **Audit log**: every work item state change must produce an immutable `AuditEntry` (who, what, when, previous state, new state). This is a domain invariant — implement it in the domain service, not in adapters.
- **Monetary values**: `BigDecimal` only — `float` and `double` are forbidden for financial amounts
- **No PII in logs**: use masked references (e.g. `workItemId`) in log messages; the audit log holds detail
- **Idempotency**: required on all inbound Kafka events and outbound API calls
- **Optimistic locking**: enforced in SQL via `WHERE id = :id AND version = :version`; `UPDATE` row-count of 0 throws `OptimisticLockingFailureException` — always
- **Maker-checker**: critical transitions configurable to require a second approver (`userId` must differ)
- **Data classification**: field classification is declared per dot-notation path in `field-type-registry` (not on WorkItem instances); the blotter masks fields for insufficient roles — enforce in the domain, not in the UI
- **Database changes**: Liquibase migration scripts only — never applied manually. Every new indexed path on `WorkItem.fields` requires a Liquibase changeset generating the PostgreSQL expression index.

## Observability (implement as part of every feature, not separately)

> Instrumentation added after the fact is always incomplete. Treat it as part of the feature's definition of done.

**Metrics** (Prometheus/Mimir via `/actuator/prometheus`): work items ingested per source, routing rule evaluation latency, workflow transition rate, outbound API latency + error rate, config reload events, blotter query latency.

**Tracing** (Tempo): propagate trace context across Kafka headers, HTTP headers, and async boundaries. Every work item carries a `correlationId` in all spans. Use Micrometer Tracing with OTLP export — no direct OTel SDK calls in domain code.

**Logs** (Loki): structured JSON only. MDC must always contain `correlationId`, `workItemId`, `userId`, `tenantId`. Log level discipline: ERROR = actionable failure, WARN = degraded state, INFO = significant state transition, DEBUG = diagnostic (off in prod).

**Profiling** (Pyroscope): Java agent on all Spring Boot apps. Capture flame graph baselines for routing and workflow modules before optimisation.

**Dashboards**: Grafana dashboard JSON per module in `/observability/dashboards/`. Alerting rules as code in `/observability/alerts/`.

## Schema and BDD Discipline

> This sequence exists to prevent the most common source of rework: discovering schema or security gaps after code is written.

**Step 1 — Plan mode (schema + scenario design)**
Every non-trivial feature starts with `EnterPlanMode`. The plan must include JSON Schema definitions for any new or changed entities and draft Cucumber scenarios covering happy path, error paths, and audit log behaviour. `ExitPlanMode` only once schemas and scenarios are agreed.

**Step 2 — Schema and feature files first**
After plan approval, the first files written are:
1. JSON schema files → `/schemas/`
2. Cucumber `.feature` files → `src/test/resources/features/<module>/`
3. In-memory port test doubles for any new output ports (in test source set)
4. Cucumber step definition stubs (failing)

**Step 3 — Verify RED**
Run `./gradlew :platform-<module>:cucumber`. New scenarios must FAIL. A green result at this stage means tests were not written first — stop and investigate.

**Step 4 — Write production code, then verify GREEN**
`./gradlew build cucumber` must pass with no failures before the task is considered done.

**Why in-memory adapters matter**: Cucumber step definitions call domain services directly via input ports using in-memory implementations — no Spring context, no database, no Kafka. New output ports must have an in-memory implementation before a BDD test can fail correctly. This is the mechanism that enforces hexagonal architecture through the test process.

## Code Coverage

> Coverage is a signal, not a target. A green coverage bar on the wrong tests is worthless. Use it to find untested behaviour, not to hit a number.

**When to review**: after every feature branch, before raising a PR. Run `./gradlew build` — JaCoCo XML reports are written to `*/build/reports/jacoco/test/jacocoTestReport.xml` for each module.

**Threshold for action**: any production class below **80% line coverage** is a gap worth addressing, unless it falls into an exempt category (see below).

**How to address gaps — Cucumber first:**
1. Ask: *does this uncovered path represent a behaviour a user or system actor could trigger?* If yes, write a Cucumber scenario in `src/test/resources/features/<module>/`. This is the default choice — scenarios document intent in business terms, serve as living documentation, and exercise the full port-to-port stack via in-memory doubles.
2. If the gap is in a pure-logic method with no meaningful BDD narrative (e.g. a domain model helper, an edge case in a validation algorithm), write a focused JUnit 5 unit test instead.
3. Adapter integration gaps (JDBC repositories, Kafka consumers) that cannot be expressed as domain-level BDD scenarios should use `io.zonky.test:embedded-postgres` or `@EmbeddedKafka` integration tests, following the pattern in `platform-api/src/test/.../adapter/out/postgres/`.

**Exempt from coverage requirements:**
- Spring `@AutoConfiguration` and `@Configuration` wiring classes — these are integration-tested implicitly when the application context starts
- `main()` entry points
- Generated code

**What to look for beyond the percentage:** uncovered *error paths* (exception branches, not-found returns, optimistic lock failures) are more dangerous than uncovered happy paths. Prioritise those.

## Coding Standards

**Java**: records for immutable domain objects; sealed interfaces for sum types; no nulls in domain layer (use `Optional`); constructor injection only; no `@Autowired` outside `config/`; all dependency versions in `libs.versions.toml`.

**TypeScript**: strict mode; no `any`; Zod for runtime validation at API boundaries.

**PostgreSQL**: every query must use an index. For `WorkItem.fields` JSONB queries: containment queries use the GIN index; field-specific queries use expression indexes declared via `field-type-registry.fieldDeclarations[].searchable`. Flag potential seq scans before implementing — check with `EXPLAIN ANALYZE`.

**Cyclomatic complexity**: individual methods must stay at ≤15. Methods approaching the limit are a signal to extract a private helper or introduce a strategy/table-driven approach — not to suppress the warning. SonarQube enforces this; treat any violation as a HIGH issue.

**Git**: conventional commits; feature branches; no direct commits to `main`.

## Definition of Done

- [ ] JSON Schema reviewed and committed
- [ ] Cucumber scenario covers happy path, error paths, and audit log behaviour
- [ ] Domain service tested with no Spring context
- [ ] Adapter tested against port contract using in-memory double
- [ ] Metrics, trace propagation, and structured logging implemented
- [ ] No PII in logs; monetary values use `BigDecimal`; audit entry produced for every state change
- [ ] `./gradlew build cucumber` passes with no failures
- [ ] `./gradlew sonar` run and all HIGH/CRITICAL issues resolved before merge
- [ ] Coverage reviewed; any production class below 80% line coverage addressed — Cucumber scenario preferred, unit test where BDD narrative doesn't apply, adapter integration test for infrastructure-only gaps
