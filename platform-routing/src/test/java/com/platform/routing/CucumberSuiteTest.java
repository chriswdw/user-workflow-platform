package com.platform.routing;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * JUnit Platform Suite entry point for Cucumber scenarios.
 * Discovers feature files under classpath:features/routing and runs step definitions
 * in com.platform.routing.steps. No Spring context — pure domain tests.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/routing")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.platform.routing.steps")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
public class CucumberSuiteTest {}
