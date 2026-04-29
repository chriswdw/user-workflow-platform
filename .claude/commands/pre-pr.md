Run the full pre-PR Definition of Done checklist for this project and report a clear pass/fail summary.

Execute these steps in order:

## Step 1 — Build and tests
Run: `./gradlew build cucumber`
Report any failures immediately. If this fails, stop and do not continue to the next steps.

## Step 2 — Coverage
Parse every JaCoCo XML report at `*/build/reports/jacoco/test/jacocoTestReport.xml`.
For each report, find production classes below 80% line coverage.

Skip these exempt categories (do not report them as gaps):
- Any class whose name contains `Application`, `AutoConfig`, or `Config` (Spring wiring)
- Any class in a test source set (path contains `/test/`)
- Any class whose name ends in `Test` or `Steps`
- Classes with 0 total lines (no executable code)

For each gap found, report: module, class name, coverage %, and lines covered/total.
Classify each gap using CLAUDE.md rules:
- User-triggerable behaviour → needs a Cucumber scenario
- Pure-logic edge case → needs a JUnit 5 unit test
- Infrastructure adapter gap → needs an embedded-postgres or @EmbeddedKafka integration test

## Step 3 — Static analysis
Run: `./scripts/sonar-check.sh`
Report pass or list of HIGH/CRITICAL issues.

## Step 4 — Summary
Print a final checklist using the Definition of Done from CLAUDE.md:
- [ ] or [x] for each item based on what was just verified
- If anything failed, list exactly what needs to be fixed before merging
