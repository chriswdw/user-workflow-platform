# Claude Skills Guide

Custom slash commands for this project. Invoke them in the Claude Code prompt.

---

## `/pre-pr`

**What it does:** Runs the full Definition of Done checklist — build, Cucumber tests, coverage gaps, and SonarQube — then prints a pass/fail summary with specific actions required.

**When to use:** Before every commit or push. Replaces the manual sequence of `./gradlew build cucumber`, parsing JaCoCo XML, and running `./scripts/sonar-check.sh` separately.

**Token saving:** Eliminates the back-and-forth of "what do I need to check before committing?" and the repeated Python snippets for parsing coverage XML.

```
/pre-pr
```

---

## `/coverage`

**What it does:** Parses all JaCoCo XML reports, finds production classes below 80% line coverage (honouring the exempt categories from CLAUDE.md), and classifies each gap as needing a Cucumber scenario, unit test, or integration test.

**When to use:** During development after adding new production code, or any time you want a standalone coverage check without running the full pre-PR suite.

**Token saving:** Replaces the ad-hoc Python parsing script that gets rewritten from scratch each session.

```
/coverage
```


---

## `/bdd-feature <description>`

**What it does:** Kicks off the project's mandatory four-step BDD-first workflow: enters plan mode, guides schema design and Cucumber scenario drafting, verifies RED, then writes production code to GREEN.

**When to use:** At the start of any non-trivial new feature. Ensures schemas and scenarios are agreed before a line of production code is written — the sequence that prevents the most common source of rework.

**Token saving:** Removes the need to re-explain the BDD workflow each session, ensures EnterPlanMode is called at the right time, and prevents skipping the RED verification step.

```
/bdd-feature routing config hot-reload
/bdd-feature file upload ingestion adapter
/bdd-feature priority scoring engine
```

---

## `/new-jdbc-adapter <PortInterface>`

**What it does:** Scaffolds a new JDBC repository adapter for a given output port — creates the repository class, integration test (using `EmbeddedPostgresProvider`), and wires it in the appropriate `@Configuration` class. Enforces all project rules: no JPA, `CAST(:x AS jsonb)`, `OffsetDateTime` for timestamps, optimistic locking, tenant isolation.

**When to use:** When implementing a new output port that needs a PostgreSQL-backed adapter. Avoids re-deriving the established patterns from scratch.

**Token saving:** The JDBC boilerplate (timestamp conversion, JSONB handling, optimistic locking, test setup) is the same every time. This skill encodes it once.

```
/new-jdbc-adapter IRoutingConfigRepository
/new-jdbc-adapter IWorkflowConfigRepository
/new-jdbc-adapter IPriorityConfigRepository
```

---

## Skill files location

`.claude/commands/` in this repository. Each `.md` file is one skill.

| File | Skill |
|---|---|
| `pre-pr.md` | `/pre-pr` |
| `coverage.md` | `/coverage` |
| `bdd-feature.md` | `/bdd-feature` |
| `new-jdbc-adapter.md` | `/new-jdbc-adapter` |
