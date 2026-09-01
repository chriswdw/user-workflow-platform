dependencies {
    testImplementation(libs.junit5.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}
