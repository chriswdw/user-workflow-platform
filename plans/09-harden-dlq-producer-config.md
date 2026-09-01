# Plan: Harden the DLQ Producer and Differentiate Retry Behavior

## Context

`platform-api/src/main/java/com/platform/api/config/KafkaIngestionConfig.java:49-58`:

```java
var dlqTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
)));
var recoverer = new DeadLetterPublishingRecoverer(dlqTemplate,
        (consumerRecord, ex) -> new TopicPartition(dlqTopic, consumerRecord.partition()));
var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
factory.setConsumerFactory(consumerFactory);
factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 0L)));
```

Two separate issues:

1. **The DLQ producer has weaker delivery guarantees than the happy-path producer.** No `acks`,
   no `enable.idempotence`, no `retries` — compare
   `platform-api/src/main/java/com/platform/api/config/DomainEventKafkaConfig.java:29-37`, which
   sets `acks=all`, `ENABLE_IDEMPOTENCE_CONFIG=true`, `RETRIES_CONFIG=3` for the same kind of
   producer. The last-resort destination for already-failed financial ingestion messages
   currently has the *least* durable delivery configuration in the module. Also, that
   `DefaultKafkaProducerFactory` is instantiated inline inside the bean method rather than exposed
   as its own managed `@Bean`, so it's never explicitly closed by the Spring lifecycle.

2. **`FixedBackOff(0L, 0L)` gives zero retries for every listener exception**, not just business
   rejections. A transient failure (a momentary DB blip during `ingestUseCase.ingest()`) gets
   routed straight to the DLQ on the first attempt, identical treatment to a genuine business
   rejection (`IngestionRejectionException` from
   `platform-api/src/main/java/com/platform/api/adapter/in/kafka/WorkItemKafkaConsumer.java`,
   thrown at line ~52 for `IngestionResult.Rejected`). The two failure classes aren't
   distinguished, so transient issues inflate the DLQ instead of self-healing on retry.

---

## Step 1 — Give the DLQ producer proper delivery guarantees and make it a managed bean

**`KafkaIngestionConfig.java`** — extract the producer factory into its own `@Bean`:

```java
@Bean
DefaultKafkaProducerFactory<String, String> dlqProducerFactory() {
    return new DefaultKafkaProducerFactory<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.ACKS_CONFIG, "all",
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1,
            ProducerConfig.RETRIES_CONFIG, 3,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true
    ));
}

@Bean
ConcurrentKafkaListenerContainerFactory<String, String> ingestionKafkaListenerContainerFactory(
        DefaultKafkaProducerFactory<String, String> dlqProducerFactory) {
    var consumerFactory = new DefaultKafkaConsumerFactory<>(Map.of( /* unchanged */ ));
    var dlqTemplate = new KafkaTemplate<>(dlqProducerFactory);
    var recoverer = new DeadLetterPublishingRecoverer(dlqTemplate,
            (consumerRecord, ex) -> new TopicPartition(dlqTopic, consumerRecord.partition()));
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler(recoverer));   // see Step 2
    return factory;
}
```
Being a `@Bean` means Spring closes it on context shutdown automatically, matching how
`DomainEventKafkaConfig.java:27-39`'s producer factory is already a first-class bean.

## Step 2 — Differentiate transient vs. business-rejection failures

Replace the single `FixedBackOff(0L, 0L)` with an error handler that retries technical exceptions
a small number of times but sends known business-rejection exceptions straight to the DLQ without
wasting retries on something that won't succeed differently the second time:

```java
private DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L)); // 2 retries, 1s apart
    handler.addNotRetryableExceptions(IngestionRejectionException.class);
    return handler;
}
```
`addNotRetryableExceptions` makes `DefaultErrorHandler` skip straight to the recoverer (DLQ) for
that exception type, while everything else gets the configured backoff first. Tune the backoff
numbers with the team — 2 retries / 1s is a starting point, not a prescription; check what
`platform-ingestion`'s other Kafka consumers (if any) use for consistency.

## Step 3 — Verify

Add an `@EmbeddedKafka` integration test (per CLAUDE.md's testing conventions — no Docker needed)
covering:
- A message causing `IngestionRejectionException` goes to the DLQ topic on the **first** attempt
  (no retry delay observed).
- A message causing a generic/transient exception (simulate via a test double throwing on the
  first N calls, succeeding after) is retried and **succeeds** without reaching the DLQ, proving
  the backoff isn't zero anymore.
- A message that exhausts retries with a persistent transient-looking exception eventually lands
  in the DLQ.

```bash
./gradlew :platform-api:test
./gradlew build cucumber
```

Coordinate with `07-fix-ingestion-duplicate-exception-handling.md` if working both — that plan's
Step 4 raises the same "should genuine failures be louder" question from the adapter side; this
plan's Step 2 is the consumer-side half of the same concern.
