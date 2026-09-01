# Plan: Replace Field `@Value` Injection with Constructor Injection

## Context

CLAUDE.md: "Constructor injection only; no `@Autowired` outside `config/`" and "Use `final` on
all injected fields." The review found four `@Configuration` classes using non-final field
`@Value` injection instead:

- `platform-api/src/main/java/com/platform/api/config/SecurityConfig.java:17,20` —
  `jwtSecret`, `requestsPerMinute`.
- `platform-api/src/main/java/com/platform/api/config/DomainEventKafkaConfig.java:21,24` —
  `bootstrapServers`, `domainEventsTopic`.
- `platform-api/src/main/java/com/platform/api/config/KafkaIngestionConfig.java:31,34,37` —
  `bootstrapServers`, `consumerGroup`, `dlqTopic`.
- `platform-api/src/main/java/com/platform/api/config/PostgresAdapterConfig.java:36` —
  `makerCheckerEnabled`.

The codebase already demonstrates the correct pattern one file over:
`platform-api/src/main/java/com/platform/api/adapter/in/rest/DevTokenController.java:32` —
`public DevTokenController(@Value("${api.jwt.secret}") String base64Secret)` — constructor
parameter, backing field `final`. This plan makes the four `@Configuration` classes consistent
with that.

This is a mechanical, low-risk cleanup — safe to batch all four in one PR.

---

## Step 1 — `SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String jwtSecret;
    private final int requestsPerMinute;

    public SecurityConfig(@Value("${api.jwt.secret}") String jwtSecret,
                           @Value("${api.rate-limit.requests-per-minute:100}") int requestsPerMinute) {
        this.jwtSecret = jwtSecret;
        this.requestsPerMinute = requestsPerMinute;
    }

    // filterChain(HttpSecurity) unchanged — already reads this.jwtSecret / this.requestsPerMinute
}
```

## Step 2 — `DomainEventKafkaConfig.java`

```java
@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class DomainEventKafkaConfig {

    private final String bootstrapServers;
    private final String domainEventsTopic;

    public DomainEventKafkaConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                                   @Value("${platform.events.topic:platform.domain-events}") String domainEventsTopic) {
        this.bootstrapServers = bootstrapServers;
        this.domainEventsTopic = domainEventsTopic;
    }

    // @Bean methods unchanged
}
```

## Step 3 — `KafkaIngestionConfig.java`

```java
@Configuration
@EnableKafka
@ConditionalOnProperty(name = {"spring.kafka.bootstrap-servers", "spring.datasource.url"})
public class KafkaIngestionConfig {

    private final String bootstrapServers;
    private final String consumerGroup;
    private final String dlqTopic;

    public KafkaIngestionConfig(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${platform.ingestion.kafka.consumer-group:platform-ingestion-group}") String consumerGroup,
            @Value("${platform.ingestion.kafka.dlq-topic:work-items.ingest.dlq}") String dlqTopic) {
        this.bootstrapServers = bootstrapServers;
        this.consumerGroup = consumerGroup;
        this.dlqTopic = dlqTopic;
    }

    // @Bean methods unchanged
}
```
If doing `09-harden-dlq-producer-config.md` at the same time, apply that plan's bean-method
changes on top of this constructor already being in place — no conflict, just do this one first.

## Step 4 — `PostgresAdapterConfig.java`

```java
@Configuration
@ConditionalOnProperty(name = "spring.datasource.url")
public class PostgresAdapterConfig {

    private final boolean makerCheckerEnabled;

    public PostgresAdapterConfig(@Value("${platform.config.maker-checker.enabled:true}") boolean makerCheckerEnabled) {
        this.makerCheckerEnabled = makerCheckerEnabled;
    }

    // @Bean methods unchanged — submissionCreationService(...) already reads this.makerCheckerEnabled
}
```

## Step 5 — Verify

This is a pure refactor with no behavior change — existing tests should pass unmodified:
```bash
./gradlew :platform-api:test
./gradlew :platform-api:cucumber
./gradlew build cucumber
```
No new scenarios needed (this isn't user-observable behavior, it's an internal wiring style
fix) — CLAUDE.md's coverage/BDD discipline applies to behavior, not to injection mechanism.

## Step 6 — Sonar

```bash
./gradlew sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token> -Dsonar.projectKey=user-workflow-platform
```
Confirm this class of issue (field injection) is/was flagged and is now clear.
