package com.platform.api.doubles;

import com.platform.domain.model.WorkItem;
import com.platform.workflow.domain.exception.ForbiddenTransitionException;
import com.platform.workflow.domain.model.TransitionCommand;
import com.platform.workflow.domain.ports.in.ITransitionWorkItemUseCase;

import java.util.HashMap;
import java.util.Map;

public class InMemoryTransitionPort implements ITransitionWorkItemUseCase {

    private final Map<String, String> transitionTargetStates = new HashMap<>();
    private final InMemoryWorkItemQueryPort workItemStore;

    public InMemoryTransitionPort(InMemoryWorkItemQueryPort workItemStore) {
        this.workItemStore = workItemStore;
    }

    public void allowTransition(String transitionName, String toState) {
        transitionTargetStates.put(transitionName, toState);
    }

    @Override
    public WorkItem transition(TransitionCommand command) {
        if ("READ_ONLY".equals(command.actorRole())) {
            throw new ForbiddenTransitionException("Role READ_ONLY cannot trigger transitions");
        }
        String targetState = transitionTargetStates.get(command.transitionName());
        if (targetState == null) {
            throw new ForbiddenTransitionException("Unknown transition: " + command.transitionName());
        }
        WorkItem current = workItemStore.findById(command.tenantId(), command.workItemId())
                .orElseThrow(() -> new IllegalStateException("Work item not found: " + command.workItemId()));
        WorkItem updated = current.withStatus(targetState).withMakerUserId(command.actorUserId());
        workItemStore.save(updated);
        return updated;
    }
}
