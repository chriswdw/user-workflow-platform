# Reconstruction Prompts

15 sequential prompts for recreating `user-workflow-platform` from scratch using an AI agent with a 1M token context window.

Run them in order. Each prompt is self-contained — it describes exactly what to create, including key implementation details, so no prior context is needed beyond "the previous steps are done."

| Prompt | Scope | Key output |
|---|---|---|
| 01 | Project scaffold | Gradle 8.8, libs.versions.toml, 8 module build files, wrapper |
| 02 | platform-domain | WorkItem, AuditEntry, ConnectionConfig (sealed), SourceType, DomainEvent, IAuditRepository, IDomainEventPublisher |
| 03 | platform-routing | ConditionNode (sealed), ConditionEvaluator, RoutingService, BDD |
| 04 | platform-workflow | WorkflowConfig, WorkflowService (state machine), BDD |
| 05 | platform-audit | AuditService, IAuditEntryRepository, BDD |
| 06 | platform-ingestion | IngestionResult (sealed), IngestionService (SHA-256 idempotency), BDD |
| 07 | platform-config-engine domain | 10 input ports, 4 output ports, 8 domain services, 7 exceptions, WorkflowTypeSubmission lifecycle |
| 08 | platform-config-engine BDD | 4 testFixtures in-memory doubles, full lifecycle Cucumber scenarios |
| 09 | platform-observability | MdcFilter, TenantAwareAuthentication, AutoConfiguration |
| 10 | platform-api database | 9 Liquibase migrations, 9 JDBC repositories |
| 11 | platform-api REST | 4 controllers, JWT filter, rate limiting, GlobalExceptionHandler, DTOs |
| 12 | platform-api wiring | PostgresAdapterConfig, DomainEventKafkaConfig, Kafka consumer/producer |
| 13 | platform-api tests | BDD (MockMvc + in-memory doubles), embedded-postgres integration tests |
| 14 | platform-frontend core | package.json, tsconfig, Zod types, utils, Zustand stores, API hooks |
| 15 | platform-frontend UI | All React components, wizard, BDD (Cucumber.js), Playwright E2E |

## Final verification
After all 15 prompts:
```bash
./gradlew build cucumber          # All Java modules
cd platform-frontend
npm run build && npm run test && npm run test:bdd && npm run test:e2e
```
