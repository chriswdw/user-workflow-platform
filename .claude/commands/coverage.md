Parse all JaCoCo coverage reports in this project and report production classes below 80% line coverage.

Run `./gradlew build` first if reports don't exist yet (check whether `*/build/reports/jacoco/test/jacocoTestReport.xml` files are present).

Then parse every `*/build/reports/jacoco/test/jacocoTestReport.xml` file found under the project root.

**Exempt — do not report these:**
- Classes whose name contains `Application`, `AutoConfig`, or ends in `Config` (Spring wiring, integration-tested implicitly)
- Classes in test source sets (path contains `/test/`)
- Class names ending in `Test`, `Steps`, `Suite`
- Classes with 0 total executable lines

**For each gap below 80%, report:**
- Module name
- Class name and coverage percentage (covered/total lines)
- Recommended fix type per CLAUDE.md:
  - **Cucumber scenario** if the uncovered path represents behaviour a user or system actor could trigger
  - **JUnit 5 unit test** if it's a pure-logic helper or edge case with no meaningful BDD narrative
  - **Integration test** (embedded-postgres / @EmbeddedKafka) if it's an infrastructure adapter gap

**Also flag:** uncovered error paths (exception branches, not-found returns, optimistic lock failures) — these are higher priority than uncovered happy paths.

Finish with a one-line summary: total gaps found, or "All production classes ≥ 80% — coverage is clean."
