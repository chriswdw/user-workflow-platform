dependencies {
    implementation(project(":platform-domain"))

    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.junit5.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.jackson.databind)
    testRuntimeOnly(libs.junit5.engine)
}

// Convenience task alias matching the CLAUDE.md command
tasks.register("cucumber") {
    group = "verification"
    description = "Runs the Cucumber BDD scenarios for platform-routing."
    dependsOn(tasks.test)
}
