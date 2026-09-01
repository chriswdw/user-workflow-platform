# Plan: Add Defense-in-Depth to Admin Authorization

## Context

`platform-api` review found admin authorization has exactly one line of defense: an inline
`if (!isPlatformAdmin(auth)) return ResponseEntity.status(403).build();` repeated in seven places,
with no URL-level enforcement backing it up:

- `platform-api/src/main/java/com/platform/api/config/ApiAuthentication.java:14` —
  `getAuthorities()` unconditionally returns `List.of()`, even though the record carries `role`.
  This means Spring Security's `hasRole()` / `@PreAuthorize` can never work against this
  principal — there is currently no way to use declarative security here at all.
- `platform-api/src/main/java/com/platform/api/config/SecurityConfig.java:30-32` — no matcher for
  `/api/v1/admin/**`; only `.anyRequest().authenticated()`.
- Manual checks: `SourceConnectionController.java:40,56,65,81,91,101` (via `isPlatformAdmin` at
  `116-118`) and `WorkflowTypeSubmissionController.java:125-127,158`.
- Minor related note: `ApiAuthentication.java:21` — `setAuthenticated(boolean)` silently no-ops
  instead of honoring the `Authentication` contract; low priority, fix opportunistically while
  in this file.

One forgotten `if` on a future admin endpoint currently means full admin-data exposure with
nothing else catching it. This plan adds the missing layer without removing the existing checks
(defense in depth, not a replacement).

---

## Step 1 — Populate authorities from the JWT role claim

**`ApiAuthentication.java:14`**
```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return role != null
            ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
            : List.of();
}
```
Add the `SimpleGrantedAuthority` import. This is a record with an existing no-op
`getAuthorities()` override — replace the body only, no constructor change needed since `role` is
already a record component.

## Step 2 — Add a URL matcher for admin endpoints

**`SecurityConfig.java:30-32`**
```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/dev/**").permitAll()
        .requestMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
        .anyRequest().authenticated())
```
This now works because Step 1 populates `ROLE_PLATFORM_ADMIN` as a granted authority.

Cross-check against `SourceConnectionController.java` — its admin endpoints are already mounted
under `/api/v1/admin/source-connections/**` (see `@PostMapping("/api/v1/admin/source-connections")`
etc.), so they're covered by this matcher without route changes. Confirm no other admin-only
endpoint lives outside the `/api/v1/admin/**` prefix; if any do, either move them under that
prefix or add a second explicit matcher for them.

## Step 3 — Replace inline checks with `@PreAuthorize` where the whole method is admin-only

For `SourceConnectionController.java` methods that are unconditionally admin-only (`adminCreate`,
`adminListAll`, `adminUpdate`, `adminDelete`, `adminGrantAccess`, `adminRevokeAccess`), remove the
manual `if (!isPlatformAdmin(auth)) return ResponseEntity.status(403).build();` guards and:
- Enable method security: add `@EnableMethodSecurity` to `SecurityConfig.java`.
- Annotate each method `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`.
- Delete the now-unused `isPlatformAdmin` helper (`SourceConnectionController.java:116-118`) if
  nothing else calls it.

For `WorkflowTypeSubmissionController.getAllDrafts` (`:125-127`) and `.discard` (`:158`): these
are **not** unconditionally admin-only — `discard` allows either the owner or an admin, so it
still needs conditional logic. Leave `discard`'s inline check as-is (it's legitimate
business-rule branching, not a missing authz gate) but add a comment noting it's intentional now
that `@PreAuthorize` handles the pure-admin cases elsewhere, so a future reader doesn't "fix" the
inconsistency by trying to force it into `@PreAuthorize` too. `getAllDrafts` **is** unconditionally
admin-only — convert it the same way as the `SourceConnectionController` methods.

## Step 4 — Fix the `Authentication` contract violation (opportunistic, low priority)

**`ApiAuthentication.java:21`** — either implement it properly (throw
`UnsupportedOperationException` if truly nothing should ever call it with `false`, which is more
honest than a silent no-op and will surface a bug immediately if wrong) or leave the existing
comment but confirm no Spring Security internals actually rely on `setAuthenticated(false)`
taking effect for this principal type before deciding. This is a nice-to-have, not the point of
this plan — don't let it block Steps 1-3.

## Step 5 — Verify

Add tests (unit or `@WebMvcTest`-style, matching existing test patterns in
`platform-api/src/test/.../adapter/in/rest/`) asserting:
- A JWT with `role=ANALYST` gets 403 hitting any `/api/v1/admin/**` route **before** the
  controller method executes (i.e., prove the filter chain rejects it, not just the controller).
- A JWT with `role=PLATFORM_ADMIN` still succeeds against the same routes (no regression).
- `WorkflowTypeSubmissionController.discard` still permits both owner-self-discard and
  admin-discard (regression check on Step 3's intentionally-untouched logic).

```bash
./gradlew :platform-api:test
./gradlew :platform-api:cucumber
./gradlew sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token> -Dsonar.projectKey=user-workflow-platform
```
