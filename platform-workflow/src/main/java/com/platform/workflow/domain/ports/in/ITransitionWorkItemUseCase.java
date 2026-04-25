package com.platform.workflow.domain.ports.in;

import com.platform.domain.model.WorkItem;
import com.platform.workflow.domain.model.TransitionCommand;

/**
 * Input port: execute a named workflow transition on a work item.
 * Returns the updated WorkItem after the transition.
 * Throws ForbiddenTransitionException, InvalidTransitionException, or ValidationFailedException on failure.
 */
public interface ITransitionWorkItemUseCase {

    WorkItem transition(TransitionCommand command);
}
