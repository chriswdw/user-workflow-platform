# Plan: Low-Priority Cleanup — Port Placement and Rate Limiter Granularity

## Context

Two minor findings from the review, bundled together since both are small and low-risk. Do these
last, or opportunistically alongside other work in the same files.

---

## Part A — Port package placement drift

CLAUDE.md's mandated layout is `domain/ports/in/` and `domain/ports/out/`. The review found:

- `platform-api/src/main/java/com/platform/api/domain/ports/IFindWorkItemPort.java`
- `platform-api/src/main/java/com/platform/api/domain/ports/IListWorkItemsPort.java`

Both live directly under `domain/ports/`, not `domain/ports/in/`. They're genuinely input-side
ports (consumed by `WorkItemController` to serve `GET` requests), so `in/` is the correct home.

Also worth noting while touching this: `platform-api` has no `domain/service/` at all —
`WorkItemJdbcRepository.java:24` implements `IFindWorkItemPort`, `IListWorkItemsPort` (these two
"input ports") *and* `IWorkItemRepository` (the `platform-workflow` module's output port)
directly on one JDBC adapter, with no domain service in between for the read path. This isn't a
layering breach — `DevConfig` supplies a second in-memory implementation of the same two
interfaces, so the project's ">1 implementation" rule for extracting an interface is satisfied —
but it's a CQRS-style read path dressed up in "port" naming that could confuse a future reader
expecting every port to sit in front of a domain service. No action required beyond the move
below; just be aware of it if this area gets touched again.

### Steps

1. Move both files:
   ```bash
   mkdir -p platform-api/src/main/java/com/platform/api/domain/ports/in
   git mv platform-api/src/main/java/com/platform/api/domain/ports/IFindWorkItemPort.java \
          platform-api/src/main/java/com/platform/api/domain/ports/in/IFindWorkItemPort.java
   git mv platform-api/src/main/java/com/platform/api/domain/ports/IListWorkItemsPort.java \
          platform-api/src/main/java/com/platform/api/domain/ports/in/IListWorkItemsPort.java
   ```
2. Update the package declaration in both files: `package com.platform.api.domain.ports.in;`
3. Update every import site — expect hits in `WorkItemController.java`, `WorkItemJdbcRepository.java`,
   `DevConfig.java`:
   ```bash
   grep -rln "com.platform.api.domain.ports.IFindWorkItemPort\|com.platform.api.domain.ports.IListWorkItemsPort" platform-api/src
   ```
   Update each import statement found (`com.platform.api.domain.ports.IFindWorkItemPort` →
   `com.platform.api.domain.ports.in.IFindWorkItemPort`, same for `IListWorkItemsPort`).
4. Build to catch anything missed:
   ```bash
   ./gradlew :platform-api:compileJava
   ```

---

## Part B — Rate limiter granularity and unbounded state

`platform-api/src/main/java/com/platform/api/config/RateLimitingFilter.java:18` —
`ConcurrentHashMap<String, Bucket> buckets` is keyed per-tenant and never evicted; long-running
deployments with many tenants accumulate buckets indefinitely (slow memory growth, not urgent).
Also, rate limiting is per-tenant only (line 33), so one misbehaving user within a tenant can
exhaust the shared budget for every other user in that tenant.

### Steps (do independently — no dependency on Part A)

1. **Bound the map.** Options, pick one and justify in the PR description:
   - Swap to a `Caffeine`-backed cache with a max size + TTL-based eviction (idle tenants'
     buckets get reclaimed). Check if `caffeine` is already in `libs.versions.toml` (likely, if
     used elsewhere for config caching) before adding a new dependency.
   - Or, simpler: add a scheduled cleanup task evicting buckets untouched for N minutes — more
     code, no new dependency.
2. **Consider per-user granularity**, if the team decides it's warranted: key the bucket map by
   `auth.tenantId() + ":" + auth.userId()` instead of `auth.tenantId()` alone
   (`RateLimitingFilter.java:33`), with the overall requests-per-minute config possibly needing a
   second, higher per-tenant ceiling layered on top so a single tenant still can't monopolize
   shared infrastructure even if no single user within it hits their own limit. This is a
   behavior change worth discussing with the team before implementing — it's not strictly a bug
   fix like the map-bounding above, it's a policy decision.
3. Verify:
   ```bash
   ./gradlew :platform-api:test
   ```
   Add/extend a `RateLimitingFilter` unit test confirming buckets for idle tenants are eventually
   evicted (if Step 1's chosen approach is testable without waiting real wall-clock time — use a
   fake clock or a very short TTL in the test).
