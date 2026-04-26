dependencies {
    implementation(project(":platform-domain"))

    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.junit.platform.suite)
    testImplementation(libs.junit5.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit5.engine)
}

tasks.register("cucumber") {
    group = "verification"
    description = "Runs the Cucumber BDD scenarios for platform-ingestion."
    dependsOn(tasks.test)
}
