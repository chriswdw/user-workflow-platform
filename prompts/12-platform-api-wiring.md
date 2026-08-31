# Prompt 12 — platform-api Wiring (Config Classes + Kafka)

## Goal
Implement the Spring `@Configuration` wiring classes that connect all the domain services and adapters together, plus the Kafka consumer and producer adapters. This is the DI wiring layer — no business logic here.

## Package: `com.platform.api.config`

### `PostgresAdapterConfig.java`
`@Configuration @ConditionalOnProperty(name = "spring.datasource.url")`

Creates beans for all 9 JDBC repositories and all domain services. Each bean's constructor injects only the ports/repositories it needs — no `@Autowired`. The `makerCheckerEnabled` flag is read via `@Value("${platform.config.maker-checker.enabled:true}")`.

```java
@Bean WorkItemJdbcRepository workItemJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper)
@Bean AuditEntryJdbcRepository auditEntryJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper)
@Bean ConfigDocumentJdbcRepository configDocumentJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper)
@Bean WorkflowConfigJdbcRepository workflowConfigJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper)
@Bean WorkflowTypeSubmissionJdbcRepository workflowTypeSubmissionJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper)
@Bean ConfigDocumentJdbcWriter configDocumentJdbcWriter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper)
@Bean SourceConnectionJdbcRepository sourceConnectionJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper)
@Bean IngestionConfigJdbcRepository ingestionConfigJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper)
@Bean IngestionWorkItemJdbcRepository ingestionWorkItemJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper)
@Bean IdempotencyKeyJdbcRepository idempotencyKeyJdbcRepository(NamedParameterJdbcTemplate jdbc)

@Bean AuditService auditService(AuditEntryJdbcRepository auditRepo)
@Bean ConfigService configService(ConfigDocumentJdbcRepository configRepo, MeterRegistry meterRegistry)
@Bean WorkflowService workflowService(WorkItemJdbcRepository workItemRepo,
                                      WorkflowConfigJdbcRepository workflowConfigRepo,
                                      AuditEntryJdbcRepository auditRepo,
                                      IDomainEventPublisher eventPublisher,
                                      MeterRegistry meterRegistry)
@Bean SubmissionCreationService submissionCreationService(WorkflowTypeSubmissionJdbcRepository repo,
                                                           ConfigDocumentJdbcWriter writer,
                                                           AuditEntryJdbcRepository auditRepo)
@Bean SubmissionDraftService submissionDraftService(WorkflowTypeSubmissionJdbcRepository repo,
                                                     AuditEntryJdbcRepository auditRepo)
@Bean SubmissionLifecycleService submissionLifecycleService(WorkflowTypeSubmissionJdbcRepository repo,
                                                             AuditEntryJdbcRepository auditRepo)
@Bean SubmissionReviewService submissionReviewService(WorkflowTypeSubmissionJdbcRepository repo,
                                                       ConfigDocumentJdbcWriter writer,
                                                       AuditEntryJdbcRepository auditRepo)
@Bean SubmissionQueryService submissionQueryService(WorkflowTypeSubmissionJdbcRepository repo)
@Bean DiscardSubmissionService discardSubmissionService(WorkflowTypeSubmissionJdbcRepository repo,
                                                         AuditEntryJdbcRepository auditRepo)
@Bean SourceConnectionService sourceConnectionService(SourceConnectionJdbcRepository repo)
```

`SubmissionCreationService` bean must pass `makerCheckerEnabled` as the fourth constructor argument.

### `DomainEventKafkaConfig.java`
`@Configuration @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")`

```java
@Value("${spring.kafka.bootstrap-servers}") private String bootstrapServers;
@Value("${platform.events.topic:platform.domain-events}") private String domainEventsTopic;

@Bean KafkaTemplate<String, String> domainEventKafkaTemplate() {
    Map<String, Object> props = Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.ACKS_CONFIG, "all",
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1,
        ProducerConfig.RETRIES_CONFIG, 3,
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true
    );
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
}

@Bean IDomainEventPublisher domainEventPublisher(KafkaTemplate<String, String> template, ObjectMapper objectMapper) {
    return new KafkaDomainEventPublisher(template, objectMapper, domainEventsTopic);
}
```

### `IngestionAdapterConfig.java`
`@Configuration @ConditionalOnProperty(name = "spring.datasource.url")`

Wires `IngestionService` with the JDBC repositories and `RoutingService` as `IGroupAssignmentPort`.

The `RoutingService` needs an `IRoutingConfigRepository` — wire a `RoutingConfigJdbcRepository` that reads from `config_documents` where `config_type='ROUTING_CONFIG'`. Alternatively, implement `IGroupAssignmentPort` as a simple adapter that calls `RoutingService.route(...)` and returns the assigned group.

### `KafkaIngestionConfig.java`
`@Configuration @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")`

Creates a `ConcurrentKafkaListenerContainerFactory` named `ingestionKafkaListenerContainerFactory`:
```java
@Bean
ConcurrentKafkaListenerContainerFactory<String, String> ingestionKafkaListenerContainerFactory(
        ConsumerFactory<String, String> consumerFactory) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumerFactory);
    return factory;
}
```

Also creates `WorkItemKafkaConsumer` bean:
```java
@Bean WorkItemKafkaConsumer workItemKafkaConsumer(IIngestRecordUseCase ingestUseCase, ObjectMapper objectMapper)
```

### `DevConfig.java`
`@Configuration @Profile("dev")`

Provides `@ConditionalOnMissingBean` fallbacks for all ports so the app starts without a database/Kafka in dev mode. Use no-op lambdas or simple in-memory implementations. Also registers a `DevTokenController` for generating test JWTs.

### `LocalDemoDataLoader.java`
`@Component @Profile("dev")` implements `ApplicationRunner`. On startup, pre-populates in-memory state with a demo `SETTLEMENT_EXCEPTION` work item and routing config for testing the UI without a database.

## Package: `com.platform.api.adapter.in.kafka`

### `IngestionRejectionException.java`
RuntimeException: used internally by `WorkItemKafkaConsumer` to trigger DLQ routing.

### `WorkItemKafkaConsumer.java`
Constructor: `(IIngestRecordUseCase ingestUseCase, ObjectMapper objectMapper)`. Not a `@Component` — wired as `@Bean` in `KafkaIngestionConfig`.

`@KafkaListener(topics = "${platform.ingestion.kafka.topic:work-items.ingest}", containerFactory = "ingestionKafkaListenerContainerFactory")`

`handle(@Payload String payload, @Headers MessageHeaders headers)`:
1. Extract `X-Correlation-ID` header (handles `byte[]` or `String`)
2. Put in MDC
3. Parse JSON payload to `RawInboundRecord`: fields `tenantId`, `workflowType`, `sourceRef`, `rawFields` (Map<String,String>)
4. Put `tenantId` in MDC
5. Call `ingestUseCase.ingest(record)`, switch on `IngestionResult`:
   - `Created` → log INFO `workItemId`, `tenantId`, `workflowType`
   - `Duplicate` → log DEBUG with idempotencyKey
   - `Rejected` → throw `IngestionRejectionException` (triggers DLQ)
6. Finally: clear MDC entries

## Package: `com.platform.api.adapter.out.kafka`

### `KafkaDomainEventPublisher.java`
Implements `IDomainEventPublisher`. Constructor: `(KafkaTemplate<String, String>, ObjectMapper, String topic)`.

`publish(DomainEvent event)`:
- Serialise event to JSON via ObjectMapper
- Create `ProducerRecord` with key = `event.workItemId()`, value = serialised JSON
- Add `X-Correlation-ID` header if `event.correlationId() != null`
- Add `eventType` header
- Call `kafkaTemplate.send(record)`
- On `JsonProcessingException`: log ERROR (never throw — domain must not fail due to event publishing)

## Verification
```bash
./gradlew :platform-api:build   # Full compile
```
