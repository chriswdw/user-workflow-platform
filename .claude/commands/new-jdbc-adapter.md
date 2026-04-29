Scaffold a new JDBC repository adapter following this project's established patterns.

The argument is the name of the output port interface to implement. Example: `/new-jdbc-adapter IRoutingConfigRepository`

## What to build

Read the target port interface first to understand the method signatures.
Then read at least one existing adapter for reference — start with `platform-api/src/main/java/com/platform/api/adapter/out/postgres/WorkItemJdbcRepository.java`.

## Mandatory patterns — every JDBC adapter must follow these

**No JPA.** Use `NamedParameterJdbcTemplate` only. Never import anything from `javax.persistence` or `org.hibernate`.

**JSONB columns:** write as `CAST(:param AS jsonb)` in SQL. Read back via `rs.getString(col)` then deserialise with Jackson `ObjectMapper`.

**Timestamps:** parameters must be `OffsetDateTime` (convert `Instant` via `OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)`). Read back with `rs.getObject(col, OffsetDateTime.class).toInstant()`.

**Optimistic locking (for UPDATE operations):** include `AND version = :version` in the WHERE clause. If `jdbc.update()` returns 0 rows, throw `OptimisticLockingFailureException`.

**Multi-tenancy:** every query must include `tenant_id` in the WHERE clause. Never allow cross-tenant queries.

**Indexes:** every query must use an index. Flag any potential seq scan with a comment before implementing.

**Constructor injection only.** No `@Autowired`. The class is a plain Java class; Spring wiring goes in a `@Configuration` class in the `config/` package.

## Files to create

1. **The repository class** — in `platform-api/src/main/java/com/platform/api/adapter/out/postgres/`
   - Implements the target port interface
   - Constructor takes `NamedParameterJdbcTemplate` and `ObjectMapper`

2. **An integration test** — in `platform-api/src/test/java/com/platform/api/adapter/out/postgres/`
   - Uses `EmbeddedPostgresProvider.DATA_SOURCE` (no Spring context, no Docker)
   - `@BeforeEach` truncates the relevant table with `TRUNCATE ... CASCADE`
   - Tests: not-found returns empty, found returns correct data, tenant isolation, error paths (optimistic lock failure if applicable)

3. **Wire it** — add a `@Bean` method to the appropriate `@Configuration` class in `config/`

## After creating

Run `./gradlew :platform-api:test --tests "<TestClassName>"` to verify all new tests pass, then run `/coverage` to confirm the adapter is above 80%.
