Start a new feature using the project's mandatory BDD-first workflow from CLAUDE.md.

The argument to this command is the feature description. Example: `/bdd-feature routing config hot-reload`

## How this works

This workflow exists to prevent the most common source of rework: discovering schema or security gaps after code is written. Follow these four steps exactly — do not skip ahead to writing production code.

## Step 1 — Enter plan mode and design schema + scenarios

Call `EnterPlanMode` now.

In plan mode:
1. Explore the relevant modules to understand existing patterns — ports, models, test doubles, existing feature files
2. Draft JSON Schema definitions for any new or changed entities (to go in `/schemas/`)
3. Draft Cucumber scenarios covering:
   - Happy path
   - All error paths (missing fields, invalid state, unauthorised role, etc.)
   - Audit log behaviour (an AuditEntry must be produced for every state change)
4. Identify which output ports are new and need in-memory test doubles
5. Present the plan (schema + scenarios) for approval before exiting plan mode

Exit plan mode only once schemas and scenarios are agreed.

## Step 2 — Write schema and feature files first (no production code yet)

After plan approval, write these files in this order:
1. JSON schema files → `/schemas/`
2. Cucumber `.feature` files → `src/test/resources/features/<module>/`
3. In-memory port test doubles for any new output ports (in test source set)
4. Cucumber step definition stubs (failing — just enough to make the scenarios runnable)

## Step 3 — Verify RED

Run `./gradlew :platform-<module>:cucumber`.

New scenarios MUST FAIL. A green result here means the tests were not written first — stop and investigate before continuing.

## Step 4 — Write production code, then verify GREEN

Now implement the production code. When done, run `./gradlew build cucumber`.

All tests must pass with no failures before the feature is considered done. Then run `/pre-pr` to complete the Definition of Done checklist.
