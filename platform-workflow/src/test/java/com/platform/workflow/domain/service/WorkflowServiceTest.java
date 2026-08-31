package com.platform.workflow.domain.service;

import com.platform.domain.model.SourceType;
import com.platform.domain.model.WorkItem;
import com.platform.workflow.domain.exception.ForbiddenTransitionException;
import com.platform.workflow.domain.exception.InvalidTransitionException;
import com.platform.workflow.domain.exception.WorkItemNotFoundException;
import com.platform.workflow.domain.exception.WorkflowConfigNotFoundException;
import com.platform.workflow.domain.model.TransitionCommand;
import com.platform.workflow.domain.model.TransitionTrigger;
import com.platform.workflow.domain.model.WorkflowConfig;
import com.platform.workflow.domain.model.WorkflowState;
import com.platform.workflow.domain.model.WorkflowTransition;
import com.platform.workflow.doubles.InMemoryWorkflowAuditRepository;
import com.platform.workflow.doubles.InMemoryWorkflowConfigRepository;
import com.platform.workflow.doubles.InMemoryWorkItemRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String WORKFLOW_TYPE = "TRADE_BREAK";

    private final InMemoryWorkItemRepository workItemRepo = new InMemoryWorkItemRepository();
    private final InMemoryWorkflowConfigRepository configRepo = new InMemoryWorkflowConfigRepository();
    private final InMemoryWorkflowAuditRepository auditRepo = new InMemoryWorkflowAuditRepository();
    private final WorkflowService service = new WorkflowService(
            workItemRepo, configRepo, auditRepo, event -> {}, new SimpleMeterRegistry());

    @BeforeEach
    void setUp() {
        configRepo.save(new WorkflowConfig(
                "cfg-1", TENANT, WORKFLOW_TYPE, "OPEN",
                List.of(
                        new WorkflowState("OPEN", false, List.of("ANALYST")),
                        new WorkflowState("CLOSED", true, List.of("ANALYST"))
                ),
                List.of(new WorkflowTransition(
                        "close", "OPEN", "CLOSED",
                        TransitionTrigger.USER_ACTION, null,
                        List.of("ANALYST"), false, List.of(), List.of()
                )),
                true));

        workItemRepo.save(workItem("wi-1", "OPEN"));
    }

    @Test
    void transition_validCommand_movesToNewState() {
        WorkItem result = service.transition(command("wi-1", "close", "ANALYST"));

        assertThat(result.status()).isEqualTo("CLOSED");
    }

    @Test
    void transition_validCommand_recordsAuditEntry() {
        service.transition(command("wi-1", "close", "ANALYST"));

        assertThat(auditRepo.all()).anyMatch(e -> "OPEN".equals(e.previousState()) && "CLOSED".equals(e.newState()));
    }

    @Test
    void transition_workItemNotFound_throwsWorkItemNotFoundException() {
        assertThatThrownBy(() -> service.transition(command("nonexistent", "close", "ANALYST")))
                .isInstanceOf(WorkItemNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void transition_noConfigForWorkflowType_throwsWorkflowConfigNotFoundException() {
        workItemRepo.save(workItem("wi-other", "OPEN").withStatus("OPEN"));
        WorkItem other = new WorkItem("wi-other", TENANT, "UNKNOWN_TYPE",
                "corr-1", null, SourceType.KAFKA, "src", "idem", "OPEN",
                "group", false, Map.of(), null, null, null, null, null, 1, "user",
                Instant.now(), Instant.now());
        workItemRepo.save(other);

        assertThatThrownBy(() -> service.transition(
                new TransitionCommand("wi-other", TENANT, "close", "user", "ANALYST", Map.of())))
                .isInstanceOf(WorkflowConfigNotFoundException.class)
                .hasMessageContaining("UNKNOWN_TYPE");
    }

    @Test
    void transition_unknownTransitionName_throwsInvalidTransitionException() {
        assertThatThrownBy(() -> service.transition(command("wi-1", "nonexistent", "ANALYST")))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void transition_wrongFromState_throwsInvalidTransitionException() {
        workItemRepo.save(workItem("wi-closed", "CLOSED"));

        assertThatThrownBy(() -> service.transition(
                new TransitionCommand("wi-closed", TENANT, "close", "user", "ANALYST", Map.of())))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("fromState");
    }

    @Test
    void transition_disallowedRole_throwsForbiddenTransitionException() {
        assertThatThrownBy(() -> service.transition(command("wi-1", "close", "READ_ONLY")))
                .isInstanceOf(ForbiddenTransitionException.class)
                .hasMessageContaining("READ_ONLY");
    }

    @Test
    void transition_withAdditionalFields_mergesFieldsIntoWorkItem() {
        TransitionCommand cmd = new TransitionCommand(
                "wi-1", TENANT, "close", "user", "ANALYST",
                Map.of("resolution.reason", "Settled"));

        WorkItem result = service.transition(cmd);

        @SuppressWarnings("unchecked")
        Map<String, Object> resolution = (Map<String, Object>) result.fields().get("resolution");
        assertThat(resolution).containsEntry("reason", "Settled");
    }

    private static TransitionCommand command(String workItemId, String transition, String role) {
        return new TransitionCommand(workItemId, TENANT, transition, "user-1", role, Map.of());
    }

    private static WorkItem workItem(String id, String status) {
        return new WorkItem(id, TENANT, WORKFLOW_TYPE, "corr-1", null,
                SourceType.KAFKA, "src", "idem",
                status, "group-ops", false, Map.of(),
                null, null, null, null, null, 1, "user",
                Instant.now(), Instant.now());
    }
}
