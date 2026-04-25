package com.platform.config.steps;

import com.platform.config.domain.exception.ConfigIntegrityException;
import com.platform.config.domain.exception.ConfigNotFoundException;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.model.ConfigValidationResult;
import com.platform.config.domain.ports.in.ILoadConfigUseCase;
import com.platform.config.domain.ports.in.IValidateConfigsUseCase;
import com.platform.config.domain.service.ConfigService;
import com.platform.config.doubles.InMemoryConfigDocumentRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions for the config engine feature.
 * Cucumber creates a new instance per scenario — all fields are per-scenario state.
 * Domain service wired with in-memory doubles; no Spring context, no database.
 */
public class ConfigStepDefinitions {

    // In-memory doubles — fresh per scenario
    private final InMemoryConfigDocumentRepository repo = new InMemoryConfigDocumentRepository();
    private final ConfigService configService = new ConfigService(repo);

    // Scenario state
    private ConfigDocument loadedDoc;
    private ConfigValidationResult validationResult;
    private Exception thrownException;
    private int docCounter = 0;

    // ── Given ────────────────────────────────────────────────────────────────

    @Given("tenant {string} has an active routing config {string} for workflow type {string}")
    public void tenantHasActiveRoutingConfigWithId(String tenantId, String id, String workflowType) {
        repo.save(routingConfig(id, tenantId, workflowType, "group-default", true));
    }

    @Given("no active routing config exists for tenant {string} and workflow type {string}")
    public void noActiveRoutingConfig(String tenantId, String workflowType) {
        // Nothing saved — absence is the precondition
    }

    @Given("tenant {string} has two active routing configs for workflow type {string}")
    public void tenantHasTwoActiveRoutingConfigs(String tenantId, String workflowType) {
        repo.save(routingConfig("config-dup-1", tenantId, workflowType, "group-default", true));
        repo.save(routingConfig("config-dup-2", tenantId, workflowType, "group-default", true));
    }

    @Given("tenant {string} has an active routing config for workflow type {string} with defaultGroup {string}")
    public void tenantHasActiveRoutingConfigWithDefaultGroup(String tenantId, String workflowType, String defaultGroup) {
        repo.save(routingConfig("config-r-" + (++docCounter), tenantId, workflowType, defaultGroup, true));
    }

    @Given("tenant {string} has an active resolution group with id {string}")
    public void tenantHasActiveResolutionGroup(String tenantId, String groupId) {
        repo.save(new ConfigDocument(
                "grp-" + groupId, tenantId, null,
                ConfigType.RESOLUTION_GROUP,
                Map.of("id", groupId, "name", groupId),
                "1", true
        ));
    }

    @Given("no resolution group with id {string} exists for tenant {string}")
    public void noResolutionGroupExists(String groupId, String tenantId) {
        // Absence is the precondition — nothing to do
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("I load the active ROUTING_CONFIG for tenant {string} and workflow type {string}")
    public void loadActiveRoutingConfig(String tenantId, String workflowType) {
        loadedDoc = configService.loadActive(tenantId, workflowType, ConfigType.ROUTING_CONFIG);
    }

    @When("I attempt to load the active ROUTING_CONFIG for tenant {string} and workflow type {string}")
    public void attemptLoadActiveRoutingConfig(String tenantId, String workflowType) {
        try {
            configService.loadActive(tenantId, workflowType, ConfigType.ROUTING_CONFIG);
        } catch (ConfigNotFoundException | ConfigIntegrityException e) {
            thrownException = e;
        }
    }

    @When("I validate configs for tenant {string}")
    public void validateConfigsForTenant(String tenantId) {
        validationResult = configService.validate(tenantId);
    }

    // ── Then ─────────────────────────────────────────────────────────────────

    @Then("the returned config document has id {string}")
    public void theReturnedConfigDocumentHasId(String expectedId) {
        assertThat(loadedDoc).isNotNull();
        assertThat(loadedDoc.id()).as("config document id").isEqualTo(expectedId);
    }

    @Then("a ConfigNotFoundException is thrown")
    public void aConfigNotFoundExceptionIsThrown() {
        assertThat(thrownException).isInstanceOf(ConfigNotFoundException.class);
    }

    @Then("a ConfigIntegrityException is thrown")
    public void aConfigIntegrityExceptionIsThrown() {
        assertThat(thrownException).isInstanceOf(ConfigIntegrityException.class);
    }

    @Then("the validation result has no violations")
    public void theValidationResultHasNoViolations() {
        assertThat(validationResult.isValid()).as("validation passed").isTrue();
        assertThat(validationResult.violations()).as("violation list").isEmpty();
    }

    @Then("the validation result has a violation mentioning {string}")
    public void theValidationResultHasAViolationMentioning(String keyword) {
        assertThat(validationResult.violations())
                .as("violations mentioning '" + keyword + "'")
                .anyMatch(v -> v.message().contains(keyword));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ConfigDocument routingConfig(String id, String tenantId,
                                                 String workflowType, String defaultGroup,
                                                 boolean active) {
        return new ConfigDocument(
                id, tenantId, workflowType,
                ConfigType.ROUTING_CONFIG,
                Map.of("defaultGroup", defaultGroup),
                "1", active
        );
    }
}
