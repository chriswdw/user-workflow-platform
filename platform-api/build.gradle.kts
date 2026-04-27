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
    implementation(libs.spring.boot.starter.actuator)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.liquibase.core)
    runtimeOnly(libs.postgresql.driver)

    testImplementation(libs.embedded.postgres)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    implementation(libs.bucket4j.core)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.spring)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.jjwt.api)
    testRuntimeOnly(libs.jjwt.impl)
    testRuntimeOnly(libs.jjwt.jackson)
}

tasks.register("cucumber") {
    group = "verification"
    description = "Runs the Cucumber BDD scenarios for platform-api."
    dependsOn(tasks.test)
}
