# Prompt 09 — platform-observability

## Goal
Implement the `platform-observability` module: MDC propagation filters, tenant-aware authentication, and the observability auto-configuration. This module is a Spring Boot library (`bootJar` disabled, `jar` enabled) that other modules pull in as a dependency.

## Package root
`com.platform.observability`

## Production files

### `MdcFilter.java`
`javax.servlet` / Jakarta `OncePerRequestFilter` that reads `X-Correlation-Id` header (falling back to generating a `UUID.randomUUID().toString()`), sets MDC keys `correlationId`, `userId`, `tenantId`, `role` from the `SecurityContextHolder.getContext().getAuthentication()` cast to `TenantAwareAuthentication` (if present), calls `chain.doFilter(...)`, then clears MDC in `finally`.

```java
public class MdcFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain)
            throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        MDC.put("correlationId", correlationId);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof TenantAwareAuthentication ta) {
            MDC.put("userId",   ta.getUserId());
            MDC.put("tenantId", ta.getTenantId());
            MDC.put("role",     ta.getRole());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### `CorrelationIdFilter.java`
`OncePerRequestFilter` that echoes the `X-Correlation-Id` (resolved the same way as in `MdcFilter`) back as a response header. Can be the same class if preferred, or a separate filter added after `MdcFilter`.

### `TenantAwareAuthentication.java`
Implements `org.springframework.security.core.Authentication`:
```java
public class TenantAwareAuthentication implements Authentication {
    private final String userId;
    private final String role;
    private final String tenantId;
    // Constructor, getters
    // getName() → userId
    // getPrincipal() → userId
    // getAuthorities() → List.of(new SimpleGrantedAuthority(role))
    // isAuthenticated() → true
    // setAuthenticated(boolean) → throw UnsupportedOperationException
    // getCredentials(), getDetails() → null
}
```

### `ObservabilityAutoConfiguration.java`
`@AutoConfiguration` class. Registers beans:
- `MdcFilter` as a `FilterRegistrationBean<MdcFilter>` with `Order(Ordered.HIGHEST_PRECEDENCE)`
- Optionally `CorrelationIdFilter` as a `FilterRegistrationBean` ordered just after MDC filter

Add `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` containing:
```
com.platform.observability.ObservabilityAutoConfiguration
```

## Constraints
- Module is a Spring Boot library: `tasks.named("bootJar") { enabled = false }` + `tasks.named("jar") { enabled = true }` (already in build.gradle.kts from Prompt 01)
- `TenantAwareAuthentication` is shared — `platform-api` and the observability module both need it. Declare it in `platform-observability` and import it from `platform-api`
- No `@Component`, `@Service`, `@Autowired` in the filter classes — everything wired through `@Bean` in the auto-config class

## No BDD tests required
The filter behaviour is verified indirectly by the `platform-api` integration tests in Prompt 13.

## Verification
```bash
./gradlew :platform-observability:build
```
