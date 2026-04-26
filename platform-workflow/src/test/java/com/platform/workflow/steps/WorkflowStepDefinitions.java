package com.platform.workflow.steps;

import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
import com.platform.domain.model.WorkItem;
import com.platform.domain.shared.FieldPathResolver;
import com.platform.workflow.domain.exception.ForbiddenTransitionException;
import com.platform.workflow.domain.exception.InvalidTransitionException;
import com.platform.workflow.domain.exception.ValidationFailedException;
import com.platform.workflow.domain.model.OnFailure;
import com.platform.workflow.domain.model.TransitionAction;
import com.platform.workflow.domain.model.TransitionActionType;
import com.platform.workflow.domain.model.TransitionCommand;
import com.platform.workflow.domain.model.TransitionTrigger;
import com.platform.workflow.domain.model.ValidationRule;
import com.platform.workflow.domain.model.WorkflowConfig;
import com.platform.workflow.domain.model.WorkflowState;
import com.platform.workflow.domain.model.WorkflowTransition;
import com.platform.workflow.domain.ports.in.ITransitionWorkItemUseCase;
import com.platform.workflow.domain.service.WorkflowService;
import com.platform.workflow.doubles.InMemoryWorkflowAuditRepository;
import com.platform.workflow.doubles.InMemoryWorkflowConfigRepository;
import com.platform.workflow.doubles.InMemoryWorkItemRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cucumber step definitions for the workflow feature.
 * Cucumber creates a new instance per scenario — all fields are per-scenario state.
 * Domain service wired with in-memory doubles; no Spring context, no database.
 */
public class WorkflowStepDefinitions {

    // In-memory doubles — fresh per scenario
    private final InMemoryWorkItemRepository workItemRepo = new InMemoryWorkItemRepository();
    private final InMemoryWorkflowConfigRepository configRepo = new InMemoryWorkflowConfigRepository();
    private final InMemoryWorkflowAuditRepository auditRepo = new InMemoryWorkflowAuditRepository();
    private final ITransitionWorkItemUseCase workflowService =
            new WorkflowService(workItemRepo, configRepo, auditRepo);

    // Scenario state
    private String tenantId;
    private String workItemId;
    private String workflowType;
    private final List<WorkflowTransition> pendingTransitions = new ArrayList<>();
    private WorkItem result;
    private Exception thrownException;

    // ── Given ────────────────────────────────────────────────────────────────

    @Given("a tenant {string} with id {string}")
    public void aTenantWithId(String name, String id) {
        this.tenantId = id;
    }

    @Given("a work item {string} of workflow type {string} in state {string} assigned to group {string}")
    public void aWorkItemInState(String id, String type, String state, String group) {
        this.workItemId = id;
        this.workflowType = type;
        WorkItem workItem = new WorkItem(
                id, tenantId, type, "corr-" + id, null,
                null, null, null,
                state, group, false, new HashMap<>(),
                null, null, null, null, null,
                1, "system", Instant.now(), Instant.now()
        );
        workItemRepo.save(workItem);
    }

    @Given("a workflow config for type {string} with transitions:")
    public void aWorkflowConfigWithTransitions(String type, DataTable dataTable) {
        this.workflowType = type;
        pendingTransitions.clear();
        List<Map<String, String>> rows = dataTable.asMaps();
        for (Map<String, String> row : rows) {
            List<String> roles = Arrays.asList(row.get("allowedRoles").split(",\\s*"));
            pendingTransitions.add(new WorkflowTransition(
                    row.get("name"),
                    row.get("fromState"),
                    row.get("toState"),
                    TransitionTrigger.USER_ACTION,
                    null,
                    roles,
                    Boolean.parseBoolean(row.get("requiresMakerChecker")),
                    List.of(),
                    List.of()
            ));
        }
        saveConfig();
    }

    @Given("the work item is in state {string}")
    public void theWorkItemIsInState(String state) {
        workItemRepo.findById(tenantId, workItemId)
                .ifPresent(item -> workItemRepo.save(item.withStatus(state)));
    }

    @Given("the workflow config has a transition {string} from {string} to {string} for role {string} with REASSIGN_GROUP action targeting {string}")
    public void addTransitionWithReassign(String name, String from, String to, String role, String targetGroup) {
        pendingTransitions.add(new WorkflowTransition(
                name, from, to,
                TransitionTrigger.USER_ACTION, null,
                List.of(role), false,
                List.of(new TransitionAction(
                        TransitionActionType.REASSIGN_GROUP,
                        Map.of("targetGroup", targetGroup),
                        OnFailure.CONTINUE
                )),
                List.of()
        ));
        saveConfig();
    }

    @Given("the workflow config has a transition {string} from {string} to {string} for role {string} requiring field {string} EXISTS")
    public void addTransitionWithExistsValidation(String name, String from, String to, String role, String field) {
        pendingTransitions.add(new WorkflowTransition(
                name, from, to,
                TransitionTrigger.USER_ACTION, null,
                List.of(role), false,
                List.of(),
                List.of(new ValidationRule(field, "EXISTS", null))
        ));
        saveConfig();
    }

    @Given("the work item has field {string} set to {string}")
    public void theWorkItemHasFieldSetTo(String dotPath, String value) {
        workItemRepo.findById(tenantId, workItemId).ifPresent(item -> {
            Map<String, Object> fields = new HashMap<>(item.fields());
            setNestedValue(fields, dotPath, value);
            workItemRepo.save(item.withFields(fields));
        });
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("user {string} with role {string} executes transition {string}")
    public void executeTransition(String userId, String role, String transitionName) {
        result = workflowService.transition(
                new TransitionCommand(workItemId, tenantId, transitionName, userId, role, java.util.Map.of()));
    }

    @When("user {string} with role {string} attempts transition {string}")
    public void attemptsTransition(String userId, String role, String transitionName) {
        try {
            workflowService.transition(
                    new TransitionCommand(workItemId, tenantId, transitionName, userId, role, java.util.Map.of()));
        } catch (ForbiddenTransitionException | InvalidTransitionException | ValidationFailedException e) {
            thrownException = e;
        }
    }

    // ── Then ─────────────────────────────────────────────────────────────────

    @Then("the work item status is {string}")
    public void theWorkItemStatusIs(String expectedState) {
        assertThat(result.status()).as("work item status").isEqualTo(expectedState);
    }

    @Then("the work item is assigned to group {string}")
    public void theWorkItemIsAssignedToGroup(String expectedGroup) {
        assertThat(result.assignedGroup()).as("assigned group").isEqualTo(expectedGroup);
    }

    @Then("a STATE_TRANSITION audit entry is written")
    public void aStateTransitionAuditEntryIsWritten() {
        assertThat(auditRepo.all())
                .anyMatch(e -> e.eventType() == AuditEventType.STATE_TRANSITION);
    }

    @Then("a GROUP_REASSIGNMENT audit entry is written")
    public void aGroupReassignmentAuditEntryIsWritten() {
        assertThat(auditRepo.all())
                .anyMatch(e -> e.eventType() == AuditEventType.GROUP_REASSIGNMENT);
    }

    @Then("a ForbiddenTransitionException is thrown")
    public void aForbiddenTransitionExceptionIsThrown() {
        assertThat(thrownException).isInstanceOf(ForbiddenTransitionException.class);
    }

    @Then("an InvalidTransitionException is thrown")
    public void anInvalidTransitionExceptionIsThrown() {
        assertThat(thrownException).isInstanceOf(InvalidTransitionException.class);
    }

    @Then("a ValidationFailedException is thrown")
    public void aValidationFailedExceptionIsThrown() {
        assertThat(thrownException).isInstanceOf(ValidationFailedException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void saveConfig() {
        List<WorkflowState> states = List.of(
                new WorkflowState("UNDER_REVIEW", false, List.of()),
                new WorkflowState("CLOSED", true, List.of()),
                new WorkflowState("COMPLIANCE_REVIEW", false, List.of())
        );
        configRepo.save(new WorkflowConfig(
                "config-1", tenantId, workflowType,
                "UNDER_REVIEW", states,
                List.copyOf(pendingTransitions), true
        ));
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> map, String dotPath, Object value) {
        int dot = dotPath.indexOf('.');
        if (dot == -1) {
            map.put(dotPath, value);
        } else {
            String head = dotPath.substring(0, dot);
            String tail = dotPath.substring(dot + 1);
            map.computeIfAbsent(head, k -> new HashMap<>());
            setNestedValue((Map<String, Object>) map.get(head), tail, value);
        }
    }
}
