plugins {
    java
}

allprojects {
    group = "com.platform"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

// Custom task: validate all JSON schemas (stub — implement in platform-config-engine)
tasks.register("validateConfigs") {
    group = "verification"
    description = "Validates all JSON configs in /schemas against their JSON Schema definitions and enforces cross-schema constraints."
    doLast {
        println("validateConfigs: not yet implemented — add implementation to platform-config-engine")
    }
}

// Generates TypeScript types from JSON schemas into platform-frontend/src/types/generated/
tasks.register<Exec>("generateTypes") {
    group = "build"
    description = "Generates Zod validators and TypeScript types from JSON schemas."
    commandLine("node", "scripts/generate-types.mjs")
}

// Runs the frontend BDD tests
tasks.register<Exec>("frontendTest") {
    group = "verification"
    description = "Runs the platform-frontend Cucumber BDD tests."
    workingDir("platform-frontend")
    commandLine("npm", "run", "test:bdd")
}

// Builds the frontend for production
tasks.register<Exec>("frontendBuild") {
    group = "build"
    description = "Builds the platform-frontend Vite application."
    workingDir("platform-frontend")
    commandLine("npm", "run", "build")
}

// Custom task: simulate priority scoring impact before deploying priorityConfig changes
tasks.register("simulatePriority") {
    group = "verification"
    description = "Simulates priority score impact of workflow-config.priorityConfig changes against live data."
    doLast {
        println("simulatePriority: not yet implemented — add implementation to platform-workflow")
    }
}
