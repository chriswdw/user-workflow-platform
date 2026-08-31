dependencies {
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}
