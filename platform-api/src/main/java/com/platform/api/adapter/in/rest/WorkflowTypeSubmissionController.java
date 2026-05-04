package com.platform.api.adapter.in.rest;

import com.platform.api.config.ApiAuthentication;
import com.platform.config.domain.exception.IncompleteSubmissionException;
import com.platform.config.domain.exception.SelfApprovalException;
import com.platform.config.domain.exception.SubmissionAlreadyExistsException;
import com.platform.config.domain.exception.SubmissionNotFoundException;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.ports.in.CreateSubmissionCommand;
import com.platform.config.domain.ports.in.ICreateWorkflowTypeSubmissionUseCase;
import com.platform.config.domain.ports.in.IGetSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviewSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviseSubmissionUseCase;
import com.platform.config.domain.ports.in.ISaveDraftUseCase;
import com.platform.config.domain.ports.in.ISubmitForApprovalUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflow-type-submissions")
public class WorkflowTypeSubmissionController {

    private final ICreateWorkflowTypeSubmissionUseCase createUseCase;
    private final ISaveDraftUseCase saveDraftUseCase;
    private final ISubmitForApprovalUseCase submitUseCase;
    private final IReviewSubmissionUseCase reviewUseCase;
    private final IReviseSubmissionUseCase reviseUseCase;
    private final IGetSubmissionUseCase getUseCase;

    public WorkflowTypeSubmissionController(
            ICreateWorkflowTypeSubmissionUseCase createUseCase,
            ISaveDraftUseCase saveDraftUseCase,
            ISubmitForApprovalUseCase submitUseCase,
            IReviewSubmissionUseCase reviewUseCase,
            IReviseSubmissionUseCase reviseUseCase,
            IGetSubmissionUseCase getUseCase) {
        this.createUseCase = createUseCase;
        this.saveDraftUseCase = saveDraftUseCase;
        this.submitUseCase = submitUseCase;
        this.reviewUseCase = reviewUseCase;
        this.reviseUseCase = reviseUseCase;
        this.getUseCase = getUseCase;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<WorkflowTypeSubmissionResponse> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ApiAuthentication auth) {
        try {
            var result = createUseCase.create(new CreateSubmissionCommand(
                    auth.tenantId(), auth.userId(),
                    (String) body.get("workflowType"),
                    (String) body.get("displayName"),
                    (String) body.get("description"),
                    parseDraftConfigs(body)));
            return ResponseEntity.status(201).body(WorkflowTypeSubmissionResponse.from(result));
        } catch (SubmissionAlreadyExistsException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @PatchMapping("/{id}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<WorkflowTypeSubmissionResponse> saveDraft(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ApiAuthentication auth) {
        try {
            int step = body.containsKey("currentStep") ? (Integer) body.get("currentStep") : 1;
            var result = saveDraftUseCase.saveDraft(
                    auth.tenantId(), id, auth.userId(),
                    parseDraftConfigs(body), step);
            return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
        } catch (SubmissionNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().build();
        }
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<WorkflowTypeSubmissionResponse> submit(
            @PathVariable String id,
            @AuthenticationPrincipal ApiAuthentication auth) {
        try {
            var result = submitUseCase.submit(auth.tenantId(), id, auth.userId());
            return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
        } catch (SubmissionNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IncompleteSubmissionException | IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().build();
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<WorkflowTypeSubmissionResponse> approve(
            @PathVariable String id,
            @AuthenticationPrincipal ApiAuthentication auth) {
        try {
            var result = reviewUseCase.approve(auth.tenantId(), id, auth.userId());
            return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
        } catch (SubmissionNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (SelfApprovalException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().build();
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<WorkflowTypeSubmissionResponse> reject(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ApiAuthentication auth) {
        try {
            String reason = (String) body.get("reason");
            var result = reviewUseCase.reject(auth.tenantId(), id, auth.userId(), reason);
            return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
        } catch (SubmissionNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (SelfApprovalException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().build();
        }
    }

    @PostMapping("/{id}/revise")
    @SuppressWarnings("unchecked")
    public ResponseEntity<WorkflowTypeSubmissionResponse> revise(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ApiAuthentication auth) {
        try {
            var result = reviseUseCase.revise(auth.tenantId(), id, auth.userId(), parseDraftConfigs(body));
            return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
        } catch (SubmissionNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().build();
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<List<WorkflowTypeSubmissionResponse>> getPending(
            @AuthenticationPrincipal ApiAuthentication auth) {
        return ResponseEntity.ok(getUseCase.getPendingForTenant(auth.tenantId())
                .stream().map(WorkflowTypeSubmissionResponse::from).toList());
    }

    @GetMapping("/all-drafts")
    public ResponseEntity<List<WorkflowTypeSubmissionResponse>> getAllDrafts(
            @AuthenticationPrincipal ApiAuthentication auth) {
        return ResponseEntity.ok(getUseCase.getAllDraftsForTenant(auth.tenantId())
                .stream().map(WorkflowTypeSubmissionResponse::from).toList());
    }

    @GetMapping("/my-drafts")
    public ResponseEntity<List<WorkflowTypeSubmissionResponse>> getMyDrafts(
            @AuthenticationPrincipal ApiAuthentication auth) {
        return ResponseEntity.ok(getUseCase.getDraftsForUser(auth.tenantId(), auth.userId())
                .stream().map(WorkflowTypeSubmissionResponse::from).toList());
    }

    @GetMapping("/my-rejected")
    public ResponseEntity<List<WorkflowTypeSubmissionResponse>> getMyRejected(
            @AuthenticationPrincipal ApiAuthentication auth) {
        return ResponseEntity.ok(getUseCase.getRejectedForUser(auth.tenantId(), auth.userId())
                .stream().map(WorkflowTypeSubmissionResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowTypeSubmissionResponse> getById(
            @PathVariable String id,
            @AuthenticationPrincipal ApiAuthentication auth) {
        try {
            return ResponseEntity.ok(
                    WorkflowTypeSubmissionResponse.from(getUseCase.getById(auth.tenantId(), id)));
        } catch (SubmissionNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @SuppressWarnings("unchecked")
    private static DraftConfigs parseDraftConfigs(Map<String, Object> body) {
        Object raw = body.get("draftConfigs");
        if (!(raw instanceof Map<?, ?> map)) {
            return new DraftConfigs(null, null, null, null, null, null);
        }
        Map<String, Object> dc = (Map<String, Object>) map;
        return new DraftConfigs(
                (Map<String, Object>) dc.get("workflowTypeDefinition"),
                (Map<String, Object>) dc.get("fieldTypeRegistry"),
                (Map<String, Object>) dc.get("ingestionSourceConfig"),
                (Map<String, Object>) dc.get("workflowConfig"),
                (Map<String, Object>) dc.get("blotterConfig"),
                (Map<String, Object>) dc.get("detailViewConfig"));
    }
}
