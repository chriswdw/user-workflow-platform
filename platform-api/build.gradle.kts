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
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.bucket4j.core)

    testImplementation(testFixtures(project(":platform-config-engine")))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.spring)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.nimbus.jose.jwt)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test:4.0.8")
}

tasks.register("cucumber") {
    group = "verification"
    description = "Runs the Cucumber BDD scenarios for platform-api."
    dependsOn(tasks.test)
}
