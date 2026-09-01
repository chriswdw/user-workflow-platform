# Plan: Close the Dev-Token Privilege-Escalation Path

## Context

Full review of `platform-api` found that four facts compound into an unauthenticated path to a
`PLATFORM_ADMIN` JWT in any deployment that doesn't explicitly override one property:

- `platform-api/src/main/java/com/platform/api/adapter/in/rest/DevTokenController.java:27` —
  `@ConditionalOnProperty(name = "api.dev.token.enabled", havingValue = "true", matchIfMissing = true)`
  — the bean is **enabled by default**.
- `platform-api/src/main/java/com/platform/api/config/SecurityConfig.java:31` —
  `.requestMatchers("/api/dev/**").permitAll()` — no authentication required to reach it.
- `platform-api/src/main/resources/application.properties:1` — a checked-in JWT HMAC secret, and
  there is no `application-prod.*` file anywhere in the module to override it or the
  `api.dev.token.enabled` flag.
- `DevTokenController.java:38-40` — `userId`, `role`, `tenantId` are taken verbatim from an
  unauthenticated request body with no allow-list, so `role=PLATFORM_ADMIN` is directly
  requestable.
- Bonus: `RateLimitingFilter.java:29-32` skips rate limiting entirely when `Authentication` is
  `null`, so this path is also unthrottled.

This is the single highest-severity finding from the review. If `01-vault-local-setup.md` hasn't
been done yet, do at least the "remove the committed secret" part of this plan regardless —
don't block a security fix on infrastructure setup.

---

## Step 1 — Flip the default to off

**`DevTokenController.java:27`**
```java
@ConditionalOnProperty(name = "api.dev.token.enabled", havingValue = "true", matchIfMissing = false)
```

## Step 2 — Explicitly enable it only where it's needed

**`platform-api/src/main/resources/application-dev.properties`** — add:
```properties
api.dev.token.enabled=true
```

**`platform-api/src/main/resources/application-local.yml`** — add:
```yaml
api:
  dev:
    token:
      enabled: true
```

Do **not** add this to `application.properties` (the base file loaded in every profile) or to
any future prod profile file.

## Step 3 — Remove the committed JWT secret default

If `01-vault-local-setup.md` is done: `application.properties:1` should already read from Vault —
skip to Step 4.

If not yet done: at minimum, stop shipping a real-looking secret as a default. Replace
`application.properties:1` with:
```properties
# api.jwt.secret must be supplied per-environment — see plans/01-vault-local-setup.md.
# No default here: an unset value should fail Spring's @Value binding at startup, not
# silently fall back to a guessable committed value.
```
and move the current test value into `application-dev.properties` / `application-local.yml`
only, where it's clearly scoped to non-production use.

## Step 4 — Add a real prod profile that fails loudly on misconfiguration

New file: **`platform-api/src/main/resources/application-prod.properties`** (or `.yml` to match
whichever style becomes the team convention):
```properties
api.dev.token.enabled=false
```
Even though Step 1's `matchIfMissing=false` already makes this the safe default, an explicit
prod-profile entry documents intent and survives someone changing the default back later.

## Step 5 — Verify

Add a focused test (unit test is fine here — this is Spring wiring behavior, not a business
scenario with a BDD narrative) asserting that with no `api.dev.token.enabled` property set and no
active profile, `DevTokenController` does **not** get registered in the application context. See
the pattern in `platform-api/src/test/.../PostgresAdapterConfigWiringTest.java` referenced in
CLAUDE.md for how this project asserts conditional bean activation.

```bash
./gradlew :platform-api:test
./gradlew :platform-api:cucumber
```

Manually confirm: start the app with no profile active, `curl -X POST localhost:8080/api/dev/token`
should now 404 (bean not registered) rather than happily returning a token.

## Step 6 — Sonar / definition of done

Run `./gradlew sonar` per CLAUDE.md and resolve any HIGH/CRITICAL issues before considering this
done — this is exactly the class of finding Sonar's security-hotspot rules are meant to catch, so
it's worth confirming it's flagged (and now, fixed) there too.
