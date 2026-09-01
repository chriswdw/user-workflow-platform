# Prompt 01 — Project Scaffold

## Goal
Create the complete Gradle multi-module project scaffold for `user-workflow-platform`. This is a configuration-driven workflow platform for financial services. After this prompt, `./gradlew build` must succeed (compiling only — no production code exists yet).

## What to create

### Root files

**`settings.gradle.kts`**
```kotlin
rootProject.name = "user-workflow-platform"

include(
    "platform-domain",
    "platform-routing",
    "platform-workflow",
    "platform-config-engine",
    "platform-ingestion",
    "platform-audit",
    "platform-api",
    "platform-observability"
)
```

**`build.gradle.kts`** (root)
- Apply `java` and `id("org.sonarqube") version "5.0.0.4638"` plugins
- `allprojects { group = "com.platform"; version = "0.0.1-SNAPSHOT"; repositories { mavenCentral() } }`
- `subprojects` block applying `java` + `jacoco`, Java 21 toolchain, `useJUnitPlatform()`, JaCoCo XML+HTML reports, 80% LINE coverage threshold, excluding `**/*Application*`, `**/*AutoConfiguration*`, `**/config/**`, `**/CucumberSuiteTest*`
- Custom root tasks: `validateConfigs` (stub), `generateTypes` (Exec: `node scripts/generate-types.mjs`), `frontendTest` (Exec: `npm run test:coverage` in `platform-frontend`), `frontendBuild` (Exec: `npm run build` in `platform-frontend`), `simulatePriority` (stub)
- `sonar` block: `sonar.projectKey=user-workflow-platform`, `sonar.projectName=User Workflow Platform`, `sonar.host.url=http://localhost:9000`, sources include `platform-frontend/src`, exclusions include `**/build/**,**/node_modules/**,**/.gradle/**,**/dist/**,**/package-lock.json`, JaCoCo XML paths from all subprojects, `sonar.javascript.lcov.reportPaths=platform-frontend/coverage/lcov.info`

**`gradle/libs.versions.toml`**
```toml
[versions]
spring-boot          = "3.3.5"
spring-dependency-mgmt = "1.1.6"
cucumber             = "7.18.1"
jjwt                 = "0.12.6"
bucket4j             = "8.10.1"
logstash-logback     = "7.4"
junit5               = "5.11.0"
junit-platform       = "1.11.0"
jackson              = "2.17.2"
assertj              = "3.26.3"
embedded-postgres    = "2.0.7"
spring-kafka         = "3.2.4"
liquibase            = "4.29.2"
postgresql           = "42.7.4"

[libraries]
cucumber-java                   = { module = "io.cucumber:cucumber-java",                   version.ref = "cucumber" }
cucumber-junit-platform-engine  = { module = "io.cucumber:cucumber-junit-platform-engine",  version.ref = "cucumber" }
cucumber-spring                 = { module = "io.cucumber:cucumber-spring",                 version.ref = "cucumber" }
jjwt-api     = { module = "io.jsonwebtoken:jjwt-api",     version.ref = "jjwt" }
jjwt-impl    = { module = "io.jsonwebtoken:jjwt-impl",    version.ref = "jjwt" }
jjwt-jackson = { module = "io.jsonwebtoken:jjwt-jackson", version.ref = "jjwt" }
bucket4j-core = { module = "com.bucket4j:bucket4j-core", version.ref = "bucket4j" }
spring-boot-starter-actuator   = { module = "org.springframework.boot:spring-boot-starter-actuator",  version.ref = "spring-boot" }
micrometer-core                = { module = "io.micrometer:micrometer-core",                           version = "1.13.5" }
micrometer-registry-prometheus = { module = "io.micrometer:micrometer-registry-prometheus" }
micrometer-tracing-bridge-otel = { module = "io.micrometer:micrometer-tracing-bridge-otel" }
otel-exporter-otlp             = { module = "io.opentelemetry:opentelemetry-exporter-otlp" }
logstash-logback-encoder       = { module = "net.logstash.logback:logstash-logback-encoder",          version.ref = "logstash-logback" }
junit-platform-suite   = { module = "org.junit.platform:junit-platform-suite",    version.ref = "junit-platform" }
junit5-api             = { module = "org.junit.jupiter:junit-jupiter-api",         version.ref = "junit5" }
junit5-engine          = { module = "org.junit.jupiter:junit-jupiter-engine",      version.ref = "junit5" }
junit5-params          = { module = "org.junit.jupiter:junit-jupiter-params",      version.ref = "junit5" }
assertj-core           = { module = "org.assertj:assertj-core",                    version.ref = "assertj" }
jackson-databind       = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
spring-boot-starter              = { module = "org.springframework.boot:spring-boot-starter",              version.ref = "spring-boot" }
spring-boot-starter-web          = { module = "org.springframework.boot:spring-boot-starter-web",          version.ref = "spring-boot" }
spring-boot-starter-data-jpa     = { module = "org.springframework.boot:spring-boot-starter-data-jpa",     version.ref = "spring-boot" }
spring-boot-starter-jdbc         = { module = "org.springframework.boot:spring-boot-starter-jdbc",         version.ref = "spring-boot" }
spring-boot-starter-validation   = { module = "org.springframework.boot:spring-boot-starter-validation",   version.ref = "spring-boot" }
spring-boot-starter-security     = { module = "org.springframework.boot:spring-boot-starter-security",     version.ref = "spring-boot" }
spring-boot-starter-test         = { module = "org.springframework.boot:spring-boot-starter-test",         version.ref = "spring-boot" }
spring-kafka      = { module = "org.springframework.kafka:spring-kafka",      version.ref = "spring-kafka" }
spring-kafka-test = { module = "org.springframework.kafka:spring-kafka-test", version.ref = "spring-kafka" }
postgresql-driver  = { module = "org.postgresql:postgresql",        version.ref = "postgresql" }
liquibase-core     = { module = "org.liquibase:liquibase-core",     version.ref = "liquibase" }
embedded-postgres  = { module = "io.zonky.test:embedded-postgres",  version.ref = "embedded-postgres" }

[plugins]
spring-boot            = { id = "org.springframework.boot",        version.ref = "spring-boot" }
spring-dependency-mgmt = { id = "io.spring.dependency-management", version.ref = "spring-dependency-mgmt" }
```

### Module `build.gradle.kts` files

**`platform-domain/build.gradle.kts`**
```kotlin
dependencies {
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
}
```

**`platform-routing/build.gradle.kts`**
```kotlin
dependencies {
    implementation(project(":platform-domain"))
    implementation(libs.micrometer.core)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.junit5.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.jackson.databind)
    testRuntimeOnly(libs.junit5.engine)
}
tasks.register("cucumber") { group = "verification"; dependsOn(tasks.test) }
```

**`platform-workflow/build.gradle.kts`** — same as routing without `jackson-databind`

**`platform-audit/build.gradle.kts`** — same as workflow without `micrometer-core`

**`platform-ingestion/build.gradle.kts`** — same as routing without `jackson-databind`

**`platform-config-engine/build.gradle.kts`**
```kotlin
plugins { `java-test-fixtures` }

dependencies {
    implementation(project(":platform-domain"))
    implementation(libs.micrometer.core)
    testFixturesImplementation(project(":platform-domain"))
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.junit5.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit5.engine)
}
tasks.register("cucumber") { group = "verification"; dependsOn(tasks.test) }
```

**`platform-observability/build.gradle.kts`**
```kotlin
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.mgmt)
}
tasks.named("bootJar") { enabled = false }
tasks.named("jar") { enabled = true }

dependencies {
    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.boot.starter.security)
    compileOnly(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.otel.exporter.otlp)
    implementation(libs.logstash.logback.encoder)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.security)
}
```

**`platform-api/build.gradle.kts`**
```kotlin
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.mgmt)
}

dependencies {
    implementation(project(":platform-domain"))
    implementation(project(":platform-config-engine"))
    implementation(project(":platform-workflow"))
    implementation(project(":platform-audit"))
    implementation(project(":platform-observability"))
    implementation(project(":platform-ingestion"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.liquibase.core)
    runtimeOnly(libs.postgresql.driver)
    testImplementation(libs.embedded.postgres)
    implementation(libs.spring.kafka)
    testImplementation(libs.spring.kafka.test)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    implementation(libs.bucket4j.core)
    testImplementation(testFixtures(project(":platform-config-engine")))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.spring)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.jjwt.api)
    testRuntimeOnly(libs.jjwt.impl)
    testRuntimeOnly(libs.jjwt.jackson)
}
tasks.register("cucumber") { group = "verification"; dependsOn(tasks.test) }
```

### Gradle wrapper
Generate using `gradle wrapper --gradle-version 8.8`. The wrapper must exist so `./gradlew` works.

### Empty `src/main/java` package stubs
For each module create the root package directory (no Java files yet):
- `platform-domain/src/main/java/com/platform/domain/`
- `platform-routing/src/main/java/com/platform/routing/`
- `platform-workflow/src/main/java/com/platform/workflow/`
- `platform-config-engine/src/main/java/com/platform/config/`
- `platform-ingestion/src/main/java/com/platform/ingestion/`
- `platform-audit/src/main/java/com/platform/audit/`
- `platform-api/src/main/java/com/platform/api/`
- `platform-observability/src/main/java/com/platform/observability/`

### `.gitignore`
Standard Java+Gradle+Node: `.gradle/`, `build/`, `*.class`, `node_modules/`, `dist/`, `.env`, `*.log`, `*.iml`, `.idea/`, `.DS_Store`

## Verification
```bash
./gradlew build   # Must succeed — no source yet, just empty modules
```
