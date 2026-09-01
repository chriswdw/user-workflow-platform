# Index: platform-api Architecture Review Follow-ups

Generated from a full read-only review of `platform-api/src/main/java/` against CLAUDE.md
(hexagonal architecture, DDD, SOLID, Clean Code, financial-services constraints) on 2026-09-01.
Branch at review time: `feat/workflow-type-submission-wizard`.

Each numbered file below is a **self-contained prompt** — paste its contents (or `cat` the file)
into a fresh Claude Code session and it has everything needed to do the work: context, exact
`file:line` citations, the fix, and how to verify it. Work through them in order; 01–04 are the
ones that actually matter for a financial-services system, the rest are real but lower stakes.

## Priority order

| # | File | Severity | One-line summary |
|---|------|----------|-------------------|
| 01 | `01-vault-local-setup.md` | — (enabler) | Set up HashiCorp Vault locally on Ubuntu; prerequisite for 02 |
| 02 | `02-fix-dev-token-privilege-escalation.md` | CRITICAL | Anonymous caller can mint a `PLATFORM_ADMIN` JWT in prod |
| 03 | `03-fix-admin-authorization-defense-in-depth.md` | HIGH | Admin authz is only inline `if`s; `getAuthorities()` is always empty |
| 04 | `04-wire-real-routing-engine.md` | HIGH | Production ingestion wiring hardcodes `group-ops`, never calls `platform-routing` |
| 05 | `05-fix-devconfig-profile-and-fake-state-machine.md` | HIGH | Dev fallback beans + fake trades active in every profile except `local` |
| 06 | `06-add-source-connection-optimistic-locking.md` | MEDIUM-HIGH | `SourceConnection` has no `version`; concurrent admin edits silently clobber |
| 07 | `07-fix-ingestion-duplicate-exception-handling.md` | MEDIUM-HIGH | Any DB constraint violation gets mislabeled + silently discarded as a duplicate |
| 08 | `08-fix-kafka-domain-event-publisher-reliability.md` | MEDIUM | Domain events can be silently dropped (swallowed exception, unchecked future) |
| 09 | `09-harden-dlq-producer-config.md` | MEDIUM | DLQ producer weaker than happy-path producer; zero-retry backoff for all errors |
| 10 | `10-constructor-injection-cleanup.md` | MEDIUM | Four `@Configuration` classes use field `@Value` injection instead of constructor |
| 11 | `11-hexagonal-and-ratelimiter-cleanup.md` | LOW | Port package placement drift; rate limiter has unbounded map + per-tenant only |

## Suggested grouping if working in batches

- **Session A (security-critical, do first):** 01, 02, 03
- **Session B (architecture correctness):** 04, 05
- **Session C (data integrity):** 06, 07
- **Session D (messaging reliability):** 08, 09
- **Session E (cleanup, do whenever):** 10, 11

## Full review transcript

The complete findings write-up (all 11 items with full reasoning, verified negative results, and
overall assessment) is in the conversation history of the session that produced this index. These
plan files distill it into actionable steps — refer back to that write-up if a plan file is
missing context you need.
