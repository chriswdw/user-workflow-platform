# Prompt 11 — platform-api REST, Security, and Request DTOs

## Goal
Implement the four REST controllers, security infrastructure (JWT filter, rate limiting, global exception handler), all request/response DTOs, additional domain ports needed by the API layer, and the Spring Boot application entry point.

## Package: `com.platform.api`

### `PlatformApiApplication.java`
```java
@SpringBootApplication
public class PlatformApiApplication {
    public static void main(String[] args) { SpringApplication.run(PlatformApiApplication.class, args); }
}
```

## Package: `com.platform.api.config`

### `ApiAuthentication.java`
Record implementing `Authentication` and `TenantAwareAuthentication`:
```java
public record ApiAuthentication(String userId, String role, String tenantId)
        implements Authentication, TenantAwareAuthentication {
    @Override public String getName() { return userId; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public Object getCredentials() { return null; }
    @Override public Object getDetails() { return null; }
    @Override public Object getPrincipal() { return this; }
    @Override public boolean isAuthenticated() { return true; }
    @Override public void setAuthenticated(boolean v) { /* immutable */ }
}
```

### `JwtAuthenticationFilter.java`
Extends `OncePerRequestFilter`. Constructor takes `String base64Secret`, decodes via `Base64.getDecoder()`, creates `SecretKey` via `Keys.hmacShaKeyFor(keyBytes)`.

`doFilterInternal`: read `Authorization: Bearer ...` header; parse with `Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload()`; extract `sub` → `userId`, `role`, `tenantId` claims; set `SecurityContextHolder.getContext().setAuthentication(new ApiAuthentication(...))`. On `JwtException`, leave context empty (Spring Security returns 401).

### `RateLimitingFilter.java`
Extends `OncePerRequestFilter`. Constructor: `int requestsPerMinute`. `ConcurrentHashMap<String, Bucket>` keyed by `tenantId`. Uses `Bucket.builder().addLimit(Bandwidth.builder().capacity(requestsPerMinute).refillGreedy(requestsPerMinute, Duration.ofMinutes(1)).build()).build()`. If `tryConsume(1)` fails, respond with `429` and `Retry-After: 60` header. Skip rate limiting when auth is null (unauthenticated requests).

### `SecurityConfig.java`
```java
@Configuration @EnableWebSecurity
public class SecurityConfig {
    @Value("${api.jwt.secret}") private String jwtSecret;
    @Value("${api.rate-limit.requests-per-minute:100}") private int requestsPerMinute;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Unauthorized")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/dev/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new RateLimitingFilter(requestsPerMinute), JwtAuthenticationFilter.class);
        return http.build();
    }
}
```

### `DevConfig.java`
`@Configuration @Profile("dev")`. Provides `@Bean` fallbacks via `@ConditionalOnMissingBean` for when `PostgresAdapterConfig` is not active (no real database configured). Returns no-op implementations or in-memory stubs so the application context starts in dev mode without a database.

## Package: `com.platform.api.domain.ports`

### `IFindWorkItemPort.java`
```java
public interface IFindWorkItemPort {
    Optional<WorkItem> findById(String tenantId, String workItemId);
}
```

### `IListWorkItemsPort.java`
```java
public interface IListWorkItemsPort {
    List<WorkItem> findByTenantAndWorkflowType(String tenantId, String workflowType);
}
```

## Package: `com.platform.api.adapter.in.rest`

### Request DTOs (all records with validation annotations)

**`TriggerTransitionRequest.java`**
```java
public record TriggerTransitionRequest(
    @NotBlank String transition,
    Map<String, Object> additionalFields
) {}
```

**`CreateSubmissionRequest.java`**
```java
public record CreateSubmissionRequest(
    @NotBlank String workflowType,
    @NotBlank String displayName,
    String description,
    DraftConfigs draftConfigs
) {}
```

**`SaveDraftRequest.java`**
```java
public record SaveDraftRequest(DraftConfigs draftConfigs, int currentStep) {}
```

**`RejectSubmissionRequest.java`**
```java
public record RejectSubmissionRequest(@NotBlank String reason) {}
```

**`ReviseSubmissionRequest.java`**
```java
public record ReviseSubmissionRequest(DraftConfigs draftConfigs) {}
```

**`SourceConnectionRequest.java`**
```java
public record SourceConnectionRequest(
    String name, String displayName, String connectionType,
    Map<String, Object> config, String credentialsRef
) {
    public ConnectionType parsedConnectionType() { return ConnectionType.valueOf(connectionType); }
    public ConnectionConfig toConnectionConfig() {
        // switch on connectionType to build KafkaConfig / DbPollConfig / FileShareConfig from config map
    }
}
```

### `WorkflowTypeSubmissionResponse.java`
Record or class with a `static from(WorkflowTypeSubmission s)` factory method, exposing all fields of the domain record.

### `WorkItemController.java`
`@RestController @RequestMapping("/api/v1/work-items")`. Constructor: `(IFindWorkItemPort, IListWorkItemsPort, ITransitionWorkItemUseCase)`.
- `GET /` → `listWorkItemsPort.findByTenantAndWorkflowType(auth.tenantId(), workflowType)`
- `GET /{id}` → `findWorkItemPort.findById(...)` mapped to 200/404
- `POST /{id}/transitions` → `transitionUseCase.transition(new TransitionCommand(id, auth.tenantId(), body.transition(), auth.userId(), auth.role(), body.additionalFields()))`

**Note on `TransitionCommand` constructor order**: check the record definition in Prompt 04 — `(tenantId, workItemId, transitionName, actorUserId, actorRole, additionalFields)`.

### `WorkflowTypeSubmissionController.java`
`@RestController @RequestMapping("/api/v1/workflow-type-submissions")`. Constructor takes all 7 use case interfaces. All endpoints documented in the conversation summary:
- `POST /` → create → 201
- `PATCH /{id}` → saveDraft → 200
- `POST /{id}/submit`, `/{id}/approve`, `/{id}/reject`, `/{id}/revise` → 200
- `GET /pending`, `/all-drafts`, `/my-drafts`, `/my-rejected` → lists → 200
- `GET /{id}` → single → 200
- `DELETE /{id}` → discard → 204

Helper: `draftConfigsOrEmpty(DraftConfigs dc)` returns `dc != null ? dc : new DraftConfigs(null, null, null, null, null, null)`.

### `AuditController.java`
`@RestController @RequestMapping("/api/v1/audit")`. Constructor: `(IQueryAuditTrailUseCase)`.
- `GET /work-items/{id}` → `queryAuditTrail.query(new AuditQuery(auth.tenantId(), id, null, null, null))` → 200

Note: `AuditQuery` has 5 fields: `tenantId, workItemId, correlationId, eventType, workItemId`. Check the record definition from Prompt 05. Pass `0`/`Integer.MAX_VALUE` for page/pageSize to return all.

### `SourceConnectionController.java`
Two path prefixes:
- `/api/v1/admin/source-connections` — admin CRUD: create (POST), list-all (GET), update (PATCH /{id}), delete (DELETE /{id}), grant-access (POST /{id}/access), revoke-access (DELETE /{id}/access/{tenantId}) — all require `PLATFORM_ADMIN` role (check `auth.role()`, return 403 otherwise)
- `/api/v1/source-connections` — analyst GET: list accessible by tenant with optional `?type=` filter

The admin `listAll()` method requires adding `listAll()` to `IListSourceConnectionsUseCase`. The analyst method uses `listAccessible(tenantId, connectionType)` — also add this to the interface. `SourceConnectionService.listAll()` delegates to `repo.findAll()` (add to `ISourceConnectionRepository`). `listAccessible(tenantId, type)` calls `repo.findByTenantId(tenantId)` filtered by type.

### `ConfigController.java`
`@RestController @RequestMapping("/api/v1/configs")`. Constructor: `(ILoadConfigUseCase)`.
- `GET /{workflowType}/{configType}` → `loadUseCase.load(auth.tenantId(), workflowType, ConfigType.valueOf(configType))` → 200

### `GlobalExceptionHandler.java`
`@RestControllerAdvice`. Returns `ProblemDetail` (Spring 6 RFC 7807) for all exceptions:
| Exception(s) | HTTP Status |
|---|---|
| `SubmissionNotFoundException`, `ConfigNotFoundException`, `WorkItemNotFoundException`, `SourceConnectionNotFoundException` | 404 |
| `WorkflowConfigNotFoundException` | 500 (log ERROR with correlationId, hide detail) |
| `SubmissionAlreadyExistsException` | 409 |
| `OptimisticLockingFailureException` | 409 ("Resource was modified concurrently — please retry") |
| `SelfApprovalException`, `ForbiddenTransitionException` | 403 |
| `IllegalArgumentException` | 400 |
| `IncompleteSubmissionException`, `InvalidTransitionException`, `ValidationFailedException`, `IllegalStateException` | 422 |
| `Exception` (catch-all) | 500 (log ERROR, return "An unexpected error occurred") |

All `ProblemDetail` responses include `correlationId` property from `MDC.get("correlationId")` when non-null.

### `DevTokenController.java`
`@RestController @RequestMapping("/api/dev") @Profile("dev")`. Provides a `GET /token` endpoint (no auth required — matches the `permitAll()` in SecurityConfig) that generates a test JWT for dev/testing. Takes query params `userId`, `role`, `tenantId`. Signs with the configured `${api.jwt.secret}`.

## Verification
```bash
./gradlew :platform-api:build
```
