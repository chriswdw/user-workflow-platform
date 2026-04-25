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

// Custom task: simulate priority scoring impact before deploying priorityConfig changes
tasks.register("simulatePriority") {
    group = "verification"
    description = "Simulates priority score impact of workflow-config.priorityConfig changes against live data."
    doLast {
        println("simulatePriority: not yet implemented — add implementation to platform-workflow")
    }
}
