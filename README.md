# user-workflow-platform

Configuration-driven workflow platform for financial services. Work items ingested from Kafka, DB polling, or file upload are routed to resolution groups and managed via a configurable web blotter. All routing, workflow, and display logic is expressed in JSON/YAML config — no hardcoded business logic in code.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Any distribution (Eclipse Temurin, Amazon Corretto, etc.) |
| Node.js | 18 LTS or later | For the React frontend only |
| PostgreSQL | 14 or later | **Local dev only** — tests do not need it |

Gradle is provided via the wrapper (`./gradlew`) — no separate installation required.

**No Docker required.** Backend tests run against an in-JVM PostgreSQL binary (`io.zonky.test:embedded-postgres`) and an in-JVM Kafka broker (`@EmbeddedKafka`). H2/HSQL are explicitly excluded — they cannot validate JSONB operators or GIN indexes.

### Installing JDK 21 on Linux

```bash
# Ubuntu / Debian
sudo apt update && sudo apt install -y temurin-21-jdk   # via Adoptium apt repo
# or via SDKMAN (distro-agnostic)
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.5-tem
```

### Installing Node.js 18+ on Linux

```bash
# via nvm (recommended)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
nvm install 20
nvm use 20
```

---

## Quickstart

```bash
git clone https://github.com/chriswdw/user-workflow-platform.git
cd user-workflow-platform

# Build everything and run all backend tests (no database or Kafka needed)
./gradlew build

# Run all BDD feature tests
./gradlew cucumber

# Install frontend dependencies and run frontend tests
cd platform-frontend && npm install && npm test
```

---

## Running locally with a real database

Tests use embedded infrastructure and need no setup. To run the Spring Boot API against a real PostgreSQL instance:

1. Install PostgreSQL (14 or later) if not already present:
   ```bash
   # Ubuntu / Debian
   sudo apt install -y postgresql
   ```

2. Create a PostgreSQL role for your OS user and set a password. The JDBC driver
   connects over TCP, so peer authentication is not sufficient — a password is required:
   ```bash
   sudo -u postgres createuser --superuser $USER
   sudo -u postgres psql -c "ALTER USER $USER WITH PASSWORD 'localdev';"
   ```

3. Create the database:
   ```bash
   createdb platform_dev
   ```

4. Create `platform-api/src/main/resources/application-local.yml` (gitignored — not committed):
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/platform_dev
       username: <your-os-username>
       password: localdev
   ```

5. Start the API:
   ```bash
   ./gradlew :platform-api:bootRun --args='--spring.profiles.active=local'
   ```

   Liquibase runs schema migrations and seeds config data automatically on first startup.
   The API will be available at `http://localhost:8080`.

6. Get a dev JWT (no credentials required in local mode):
   ```bash
   curl -s -X POST http://localhost:8080/api/dev/token \
     -H "Content-Type: application/json" \
     -d '{"userId":"dev-user","role":"ANALYST","tenantId":"tenant-1"}' \
     | jq -r .token
   ```

---

## Key commands

```bash
./gradlew build                          # Full build + unit + integration tests
./gradlew cucumber                       # All BDD feature tests across all modules
./gradlew :platform-api:test             # Single module
./gradlew validateConfigs                # Validate JSON configs against schemas
./gradlew generateTypes                  # Generate TypeScript types from schemas
./gradlew simulatePriority \
  --workflowType=SETTLEMENT_EXCEPTION    # Simulate priority scoring changes

# Frontend
cd platform-frontend
npm run dev                              # Vite dev server at http://localhost:5173
npm test                                 # Jest unit tests
npm run test:bdd                         # Cucumber.js BDD tests
npm run test:e2e                         # Playwright E2E tests (headless Chromium)
npm run test:e2e:ui                      # Playwright E2E tests with interactive UI
npm run build                            # Production build
```

---

## Frontend E2E tests (Playwright)

E2E tests run a real Chromium browser against the Vite dev server. Backend calls are intercepted with `page.route()` — no Spring Boot instance is required.

### First-time setup

```bash
cd platform-frontend
npm install
npx playwright install chromium
```

### Running the tests

```bash
# Run all E2E specs headlessly (CI / pre-PR)
npm run test:e2e

# Open the interactive Playwright UI (step-through debugging)
npm run test:e2e:ui

# Run a single spec with a visible browser window
npx playwright test e2e/admin-nav.spec.ts --headed

# View the HTML report from the last run
npx playwright show-report
```

`npm run test:e2e` reuses an already-running `npm run dev` server if one is on port 5173; otherwise it starts one automatically.

### Updating visual snapshots

The `wizard-step2-layout` spec includes a pixel-diff screenshot of the wizard step 2 body. After any intentional CSS change, regenerate the baseline and commit the updated PNG:

```bash
npx playwright test e2e/wizard-step2-layout.spec.ts --update-snapshots=all
git add e2e/wizard-step2-layout.spec.ts-snapshots/
```

### What the specs cover

| Spec | Tests | What it catches |
|---|---|---|
| `admin-nav.spec.ts` | 4 | Admin-only nav items visible/hidden by role; `All Drafts` and `Source Connections` views render correctly |
| `wizard-step2-layout.spec.ts` | 2 | Radio buttons render inline (bounding-box check); CSS regressions in step 2 (visual snapshot) |
| `wizard-api-urls.spec.ts` | 5 | Save-draft PATCH URL has no `/draft` suffix; GET hooks use sub-path style (`/pending`, `/my-drafts`, `/my-rejected`) not query params |

---

## Static analysis (SonarQube)

SonarQube is optional for local development but required before every PR. A local instance can be started with Docker:

```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
```

Once running at `http://localhost:9000` (default credentials `admin` / `admin`):

1. Create a project with key `user-workflow-platform`
2. Generate a project analysis token
3. Run analysis:

```bash
./gradlew sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=<your-token> \
  -Dsonar.projectKey=user-workflow-platform
```

All HIGH and CRITICAL issues must be resolved before merging. See [CLAUDE.md](CLAUDE.md) for the full definition of done.

---

## Project structure

```
platform-domain/          — Shared domain model (WorkItem, AuditEntry, etc.)
platform-config-engine/   — Config loading, validation, hot-reload from PostgreSQL
platform-ingestion/       — Kafka consumer, DB polling, file upload → WorkItem
platform-routing/         — Rule evaluator + group assignment (pure domain)
platform-workflow/        — State machine and transition execution
platform-audit/           — Immutable audit log
platform-api/             — Spring MVC REST API, JWT security, rate limiting
platform-observability/   — Shared Micrometer config, structured logging
platform-frontend/        — React + Vite blotter application
schemas/                  — JSON Schema definitions for all config and domain types
docker/observability/     — Grafana stack (Prometheus, Loki, Tempo, Pyroscope)
```

See [CLAUDE.md](CLAUDE.md) for architecture decisions, coding standards, and the full definition of done.
