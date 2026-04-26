package com.platform.api.adapter.in.rest;

import com.platform.api.config.ApiAuthentication;
import com.platform.api.domain.ports.IFindWorkItemPort;
import com.platform.api.domain.ports.IListWorkItemsPort;
import com.platform.domain.model.WorkItem;
import com.platform.workflow.domain.exception.ForbiddenTransitionException;
import com.platform.workflow.domain.model.TransitionCommand;
import com.platform.workflow.domain.ports.in.ITransitionWorkItemUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/work-items")
public class WorkItemController {

    private final IFindWorkItemPort findWorkItemPort;
    private final IListWorkItemsPort listWorkItemsPort;
    private final ITransitionWorkItemUseCase transitionUseCase;

    public WorkItemController(IFindWorkItemPort findWorkItemPort,
                               IListWorkItemsPort listWorkItemsPort,
                               ITransitionWorkItemUseCase transitionUseCase) {
        this.findWorkItemPort = findWorkItemPort;
        this.listWorkItemsPort = listWorkItemsPort;
        this.transitionUseCase = transitionUseCase;
    }

    @GetMapping
    public ResponseEntity<List<WorkItem>> listWorkItems(
            @RequestParam String workflowType,
            @AuthenticationPrincipal ApiAuthentication auth) {
        return ResponseEntity.ok(
                listWorkItemsPort.findByTenantAndWorkflowType(auth.tenantId(), workflowType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkItem> getWorkItem(@PathVariable String id,
                                                 @AuthenticationPrincipal ApiAuthentication auth) {
        return findWorkItemPort.findById(auth.tenantId(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/transitions")
    @SuppressWarnings("unchecked")
    public ResponseEntity<WorkItem> triggerTransition(@PathVariable String id,
                                                       @RequestBody Map<String, Object> body,
                                                       @AuthenticationPrincipal ApiAuthentication auth) {
        try {
            Map<String, Object> additionalFields = body.containsKey("additionalFields")
                    ? (Map<String, Object>) body.get("additionalFields")
                    : Map.of();
            WorkItem updated = transitionUseCase.transition(new TransitionCommand(
                    id, auth.tenantId(), (String) body.get("transition"),
                    auth.userId(), auth.role(), additionalFields));
            return ResponseEntity.ok(updated);
        } catch (ForbiddenTransitionException e) {
            return ResponseEntity.status(403).build();
        }
    }
}
