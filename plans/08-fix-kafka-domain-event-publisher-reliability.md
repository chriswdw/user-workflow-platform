# Plan: Stop Silently Dropping Domain Events

## Context

`platform-api/src/main/java/com/platform/api/adapter/out/kafka/KafkaDomainEventPublisher.java:31-48`
has two reliability gaps in the `publish()` method:

```java
@Override
public void publish(DomainEvent event) {
    try {
        String payload = objectMapper.writeValueAsString(event);
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, event.workItemId(), payload);
        // ... headers ...
        kafkaTemplate.send(producerRecord);
    } catch (JacksonException e) {
        log.error("correlationId={} Failed to serialise domain event type={}", event.correlationId(), event.eventType(), e);
    }
}
```

1. **Line 45-47**: if serialization fails, the event is dropped — logged, not rethrown, no
   dead-letter, no retry, no caller-visible signal at all.
2. **Line 44**: `kafkaTemplate.send(producerRecord)` returns a `CompletableFuture` that's never
   inspected. `DomainEventKafkaConfig.java:33-36` configures `acks=all` + idempotence, which
   protects against *duplicate* delivery on retry, but does nothing for an unacknowledged failed
   send that nobody ever looks at — the caller has already moved on by the time (if ever) the
   future completes exceptionally.

This is distinct from the audit log, which persists separately via `IAuditRepository` to Postgres
and is not at risk here — this is specifically about domain events (the mechanism other services
and observability consume for downstream effects and trace continuity).

---

## Step 1 — Decide the failure-handling policy

Before writing code, decide (this affects callers of `publish()` across the domain services that
inject `IDomainEventPublisher` — `IngestionService`, `WorkflowService`, submission services):
- Should a publish failure be visible to the caller (checked/unchecked exception from `publish()`)
  so a domain service can decide to fail the whole operation, or should it stay fire-and-forget
  but with actual observability (metric + alertable log) instead of a swallowed exception?
- Given `IDomainEventPublisher.publish(DomainEvent)` returns `void` today and is called from
  several domain services *after* their own state has already been persisted (see
  `IngestionService.java` — `eventPublisher.publish(...)` runs after `workItemRepository.save()`
  succeeds), making it a hard failure would mean rolling back or compensating for an already-
  committed state change, which is more invasive. The lower-risk fix that still closes the gap:
  keep `publish()` `void`/non-throwing from the caller's perspective, but make failures loud
  (ERROR log + metric) instead of silent, and add basic resilience (retry a fixed number of times)
  before giving up. Confirm this reasoning holds before implementing — if there's an existing
  outbox/retry mechanism elsewhere in the codebase for domain events, prefer wiring into that
  instead of inventing a second one.

## Step 2 — Handle the serialization failure loudly

**`KafkaDomainEventPublisher.java:45-47`** — at minimum, increment a metric so this is visible in
Grafana (per CLAUDE.md's Observability section, this module should already have `MeterRegistry`
available or easily injectable):
```java
} catch (JacksonException e) {
    meterRegistry.counter("domain_events.publish.failure", "reason", "serialization").increment();
    log.error("correlationId={} Failed to serialise domain event type={}", event.correlationId(), event.eventType(), e);
}
```
Add `MeterRegistry` as a constructor dependency (constructor injection, per CLAUDE.md) alongside
the existing `KafkaTemplate`/`ObjectMapper`/`topic` params, and update
`DomainEventKafkaConfig.java:42-45`'s bean method to pass it through.

## Step 3 — Handle the async send failure

**`KafkaDomainEventPublisher.java:44`**
```java
kafkaTemplate.send(producerRecord).whenComplete((result, ex) -> {
    if (ex != null) {
        meterRegistry.counter("domain_events.publish.failure", "reason", "send").increment();
        log.error("correlationId={} Failed to send domain event type={} to topic={}",
                event.correlationId(), event.eventType(), topic, ex);
    }
});
```
Confirm `kafkaTemplate.send()`'s return type in the Spring Kafka version this project pins
(`libs.versions.toml`) — it should be `CompletableFuture<SendResult<K, V>>` in recent Spring Kafka,
adjust the lambda signature if it's still `ListenableFuture` on an older version.

## Step 4 — Add the metric to the observability dashboard

Per CLAUDE.md: "Dashboards: Grafana dashboard JSON per module in `/observability/dashboards/`."
Add `domain_events.publish.failure` to `platform-api`'s dashboard JSON (find the existing file
under `/observability/dashboards/` — likely `platform-api.json` or similar) and consider whether
it warrants an alerting rule under `/observability/alerts/` given a spike here means downstream
consumers are missing events.

## Step 5 — Verify

Add a unit test for `KafkaDomainEventPublisher` (no Spring context needed — this is a plain class
with constructor-injected dependencies, mock `KafkaTemplate`/`ObjectMapper`/`MeterRegistry`)
covering:
- Successful publish increments no failure counter.
- A `JacksonException` during serialization increments the `serialization`-tagged counter and
  logs at ERROR, without throwing out of `publish()`.
- A `send()` future completing exceptionally increments the `send`-tagged counter and logs at
  ERROR (use a manually-completed `CompletableFuture` in the mock to simulate this
  synchronously in the test).

```bash
./gradlew :platform-api:test
./gradlew build cucumber
```
