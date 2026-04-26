package com.platform.workflow.domain.service;

import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
import com.platform.domain.model.WorkItem;
import com.platform.domain.shared.FieldPathResolver;
import com.platform.workflow.domain.exception.ForbiddenTransitionException;
import com.platform.workflow.domain.exception.InvalidTransitionException;
import com.platform.workflow.domain.exception.ValidationFailedException;
import com.platform.workflow.domain.model.TransitionAction;
import com.platform.workflow.domain.model.TransitionCommand;
import com.platform.workflow.domain.model.ValidationRule;
import com.platform.workflow.domain.model.WorkflowConfig;
import com.platform.workflow.domain.model.WorkflowTransition;
import com.platform.workflow.domain.ports.in.ITransitionWorkItemUseCase;
import com.platform.workflow.domain.ports.out.IWorkflowAuditRepository;
import com.platform.workflow.domain.ports.out.IWorkflowConfigRepository;
import com.platform.workflow.domain.ports.out.IWorkItemRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain service implementing the workflow state machine.
 * No framework dependencies — constructor-injected output ports only.
 *
 * Algorithm:
 * 1. Load work item and active workflow config
 * 2. Find the named transition; validate fromState matches current status
 * 3. Check actor role is in transition.allowedRoles
 * 4. Evaluate validationRules against work item fields — fail fast on first violation
 * 5. Execute REASSIGN_GROUP actions (other action types deferred to adapter layer via events)
 * 6. Apply state transition, write audit entry, save work item
 */
public class WorkflowService implements ITransitionWorkItemUseCase {

    private final IWorkItemRepository workItemRepository;
    private final IWorkflowConfigRepository workflowConfigRepository;
    private final IWorkflowAuditRepository auditRepository;

    public WorkflowService(IWorkItemRepository workItemRepository,
                           IWorkflowConfigRepository workflowConfigRepository,
                           IWorkflowAuditRepository auditRepository) {
        this.workItemRepository = workItemRepository;
        this.workflowConfigRepository = workflowConfigRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public WorkItem transition(TransitionCommand command) {
        WorkItem workItem = workItemRepository.findById(command.tenantId(), command.workItemId())
                .orElseThrow(() -> new IllegalStateException(
                        "WorkItem not found: " + command.workItemId()));

        WorkflowConfig config = workflowConfigRepository
                .findActiveByTenantAndWorkflowType(workItem.tenantId(), workItem.workflowType())
                .orElseThrow(() -> new IllegalStateException(
                        "No active workflow config for workflowType=" + workItem.workflowType()
                        + ", tenantId=" + workItem.tenantId()));

        WorkflowTransition transition = config.findTransition(command.transitionName())
                .orElseThrow(() -> new InvalidTransitionException(
                        "Unknown transition: " + command.transitionName()));

        // Validate current state matches fromState
        if (!transition.fromState().equals(workItem.status())) {
            throw new InvalidTransitionException(
                    "Transition '" + command.transitionName() + "' requires fromState='"
                    + transition.fromState() + "' but work item is in state='" + workItem.status() + "'");
        }

        // Check role authorisation
        if (!transition.allowedRoles().contains(command.actorRole())) {
            throw new ForbiddenTransitionException(
                    "Role '" + command.actorRole() + "' is not permitted to execute transition '"
                    + command.transitionName() + "'");
        }

        // Evaluate validation rules
        for (ValidationRule rule : transition.validationRules()) {
            if (!passes(rule, workItem)) {
                throw new ValidationFailedException(
                        "Validation failed for field '" + rule.field() + "' (operator="
                        + rule.operator() + ")");
            }
        }

        // Merge user-supplied additional fields from the action form.
        // Fields use dot-notation paths (e.g. "resolution.reason") and must be nested
        // into the fields map consistently with how FieldPathResolver resolves them.
        WorkItem updated = workItem;
        if (!command.additionalFields().isEmpty()) {
            java.util.Map<String, Object> merged = new java.util.HashMap<>(workItem.fields());
            List<AuditEntry.ChangedField> fieldChanges = command.additionalFields().entrySet().stream()
                    .map(e -> new AuditEntry.ChangedField(
                            e.getKey(),
                            FieldPathResolver.resolve(merged, e.getKey()).orElse(null),
                            e.getValue()))
                    .toList();
            command.additionalFields().forEach((path, value) -> setNestedValue(merged, path, value));
            updated = updated.withFields(java.util.Collections.unmodifiableMap(merged));
            auditRepository.save(fieldUpdateAuditEntry(command, updated, fieldChanges));
        }

        // Apply synchronous REASSIGN_GROUP actions
        String previousGroup = updated.assignedGroup();
        for (TransitionAction action : transition.actions()) {
            if (action.type() == com.platform.workflow.domain.model.TransitionActionType.REASSIGN_GROUP) {
                String targetGroup = action.config().get("targetGroup");
                updated = updated.withAssignedGroup(targetGroup);
                auditRepository.save(groupReassignmentAuditEntry(command, workItem, previousGroup, targetGroup));
            }
        }

        // Apply state transition
        String previousState = updated.status();
        updated = updated.withStatus(transition.toState())
                         .withMakerUserId(command.actorUserId());

        auditRepository.save(stateTransitionAuditEntry(command, updated, previousState, transition.toState()));

        return workItemRepository.save(updated);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean passes(ValidationRule rule, WorkItem workItem) {
        return switch (rule.operator()) {
            case "EXISTS" -> FieldPathResolver.resolve(workItem.fields(), rule.field()).isPresent();
            case "EQ" -> FieldPathResolver.resolve(workItem.fields(), rule.field())
                    .map(v -> v.toString().equals(rule.value()))
                    .orElse(false);
            case "NEQ" -> FieldPathResolver.resolve(workItem.fields(), rule.field())
                    .map(v -> !v.toString().equals(rule.value()))
                    .orElse(false);
            default -> throw new IllegalArgumentException("Unsupported validation operator: " + rule.operator());
        };
    }

    private static AuditEntry stateTransitionAuditEntry(TransitionCommand command,
                                                         WorkItem workItem,
                                                         String previousState,
                                                         String newState) {
        return new AuditEntry(
                UUID.randomUUID().toString(),
                workItem.tenantId(),
                workItem.id(),
                workItem.correlationId(),
                AuditEventType.STATE_TRANSITION,
                previousState,
                newState,
                command.transitionName(),
                List.of(new AuditEntry.ChangedField("status", previousState, newState)),
                command.actorUserId(),
                command.actorRole(),
                Instant.now(),
                workItem.id() + ":" + command.transitionName()
        );
    }

    private static AuditEntry groupReassignmentAuditEntry(TransitionCommand command,
                                                            WorkItem workItem,
                                                            String previousGroup,
                                                            String newGroup) {
        return new AuditEntry(
                UUID.randomUUID().toString(),
                workItem.tenantId(),
                workItem.id(),
                workItem.correlationId(),
                AuditEventType.GROUP_REASSIGNMENT,
                null,
                null,
                command.transitionName(),
                List.of(new AuditEntry.ChangedField("assignedGroup", previousGroup, newGroup)),
                command.actorUserId(),
                command.actorRole(),
                Instant.now(),
                workItem.id() + ":" + command.transitionName() + ":reassign"
        );
    }

    private static AuditEntry fieldUpdateAuditEntry(TransitionCommand command,
                                                      WorkItem workItem,
                                                      List<AuditEntry.ChangedField> changedFields) {
        return new AuditEntry(
                UUID.randomUUID().toString(),
                workItem.tenantId(),
                workItem.id(),
                workItem.correlationId(),
                AuditEventType.FIELD_UPDATE,
                null,
                null,
                command.transitionName(),
                changedFields,
                command.actorUserId(),
                command.actorRole(),
                Instant.now(),
                workItem.id() + ":" + command.transitionName() + ":fields"
        );
    }

    @SuppressWarnings("unchecked")
    private static void setNestedValue(java.util.Map<String, Object> map, String dotPath, Object value) {
        int dot = dotPath.indexOf('.');
        if (dot == -1) {
            map.put(dotPath, value);
        } else {
            String head = dotPath.substring(0, dot);
            String tail = dotPath.substring(dot + 1);
            map.computeIfAbsent(head, k -> new java.util.HashMap<>());
            setNestedValue((java.util.Map<String, Object>) map.get(head), tail, value);
        }
    }
}
