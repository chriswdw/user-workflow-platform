package com.platform.api.adapter.in.rest;

import com.platform.api.config.ApiAuthentication;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.ports.in.CreateSubmissionCommand;
import com.platform.config.domain.ports.in.ICreateWorkflowTypeSubmissionUseCase;
import com.platform.config.domain.ports.in.IDiscardSubmissionUseCase;
import com.platform.config.domain.ports.in.IGetSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviewSubmissionUseCase;
import com.platform.config.domain.ports.in.IReviseSubmissionUseCase;
import com.platform.config.domain.ports.in.ISaveDraftUseCase;
import com.platform.config.domain.ports.in.ISubmitForApprovalUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-type-submissions")
public class WorkflowTypeSubmissionController {

  private final ICreateWorkflowTypeSubmissionUseCase createUseCase;
  private final ISaveDraftUseCase saveDraftUseCase;
  private final ISubmitForApprovalUseCase submitUseCase;
  private final IReviewSubmissionUseCase reviewUseCase;
  private final IReviseSubmissionUseCase reviseUseCase;
  private final IGetSubmissionUseCase getUseCase;
  private final IDiscardSubmissionUseCase discardUseCase;

  public WorkflowTypeSubmissionController(
      ICreateWorkflowTypeSubmissionUseCase createUseCase,
      ISaveDraftUseCase saveDraftUseCase,
      ISubmitForApprovalUseCase submitUseCase,
      IReviewSubmissionUseCase reviewUseCase,
      IReviseSubmissionUseCase reviseUseCase,
      IGetSubmissionUseCase getUseCase,
      IDiscardSubmissionUseCase discardUseCase) {
    this.createUseCase = createUseCase;
    this.saveDraftUseCase = saveDraftUseCase;
    this.submitUseCase = submitUseCase;
    this.reviewUseCase = reviewUseCase;
    this.reviseUseCase = reviseUseCase;
    this.getUseCase = getUseCase;
    this.discardUseCase = discardUseCase;
  }

  @PostMapping
  public ResponseEntity<WorkflowTypeSubmissionResponse> create(
      @Valid @RequestBody CreateSubmissionRequest body,
      @AuthenticationPrincipal ApiAuthentication auth) {
    var result =
        createUseCase.create(
            new CreateSubmissionCommand(
                auth.tenantId(),
                auth.userId(),
                body.workflowType(),
                body.displayName(),
                body.description(),
                draftConfigsOrEmpty(body.draftConfigs())));
    return ResponseEntity.status(201).body(WorkflowTypeSubmissionResponse.from(result));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<WorkflowTypeSubmissionResponse> saveDraft(
      @PathVariable String id,
      @RequestBody SaveDraftRequest body,
      @AuthenticationPrincipal ApiAuthentication auth) {
    var result =
        saveDraftUseCase.saveDraft(
            auth.tenantId(),
            id,
            auth.userId(),
            draftConfigsOrEmpty(body.draftConfigs()),
            body.currentStep());
    return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
  }

  @PostMapping("/{id}/submit")
  public ResponseEntity<WorkflowTypeSubmissionResponse> submit(
      @PathVariable String id, @AuthenticationPrincipal ApiAuthentication auth) {
    var result = submitUseCase.submit(auth.tenantId(), id, auth.userId());
    return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
  }

  @PostMapping("/{id}/approve")
  public ResponseEntity<WorkflowTypeSubmissionResponse> approve(
      @PathVariable String id, @AuthenticationPrincipal ApiAuthentication auth) {
    var result = reviewUseCase.approve(auth.tenantId(), id, auth.userId());
    return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
  }

  @PostMapping("/{id}/reject")
  public ResponseEntity<WorkflowTypeSubmissionResponse> reject(
      @PathVariable String id,
      @RequestBody RejectSubmissionRequest body,
      @AuthenticationPrincipal ApiAuthentication auth) {
    var result = reviewUseCase.reject(auth.tenantId(), id, auth.userId(), body.reason());
    return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
  }

  @PostMapping("/{id}/revise")
  public ResponseEntity<WorkflowTypeSubmissionResponse> revise(
      @PathVariable String id,
      @RequestBody ReviseSubmissionRequest body,
      @AuthenticationPrincipal ApiAuthentication auth) {
    var result =
        reviseUseCase.revise(
            auth.tenantId(), id, auth.userId(), draftConfigsOrEmpty(body.draftConfigs()));
    return ResponseEntity.ok(WorkflowTypeSubmissionResponse.from(result));
  }

  @GetMapping("/pending")
  public ResponseEntity<List<WorkflowTypeSubmissionResponse>> getPending(
      @AuthenticationPrincipal ApiAuthentication auth) {
    return ResponseEntity.ok(
        getUseCase.getPendingForTenant(auth.tenantId()).stream()
            .map(WorkflowTypeSubmissionResponse::from)
            .toList());
  }

  @GetMapping("/all-drafts")
  public ResponseEntity<List<WorkflowTypeSubmissionResponse>> getAllDrafts(
      @AuthenticationPrincipal ApiAuthentication auth) {
    if (!"PLATFORM_ADMIN".equals(auth.role())) {
      return ResponseEntity.status(403).build();
    }
    return ResponseEntity.ok(
        getUseCase.getAllDraftsForTenant(auth.tenantId()).stream()
            .map(WorkflowTypeSubmissionResponse::from)
            .toList());
  }

  @GetMapping("/my-drafts")
  public ResponseEntity<List<WorkflowTypeSubmissionResponse>> getMyDrafts(
      @AuthenticationPrincipal ApiAuthentication auth) {
    return ResponseEntity.ok(
        getUseCase.getDraftsForUser(auth.tenantId(), auth.userId()).stream()
            .map(WorkflowTypeSubmissionResponse::from)
            .toList());
  }

  @GetMapping("/my-rejected")
  public ResponseEntity<List<WorkflowTypeSubmissionResponse>> getMyRejected(
      @AuthenticationPrincipal ApiAuthentication auth) {
    return ResponseEntity.ok(
        getUseCase.getRejectedForUser(auth.tenantId(), auth.userId()).stream()
            .map(WorkflowTypeSubmissionResponse::from)
            .toList());
  }

  @GetMapping("/{id}")
  public ResponseEntity<WorkflowTypeSubmissionResponse> getById(
      @PathVariable String id, @AuthenticationPrincipal ApiAuthentication auth) {
    return ResponseEntity.ok(
        WorkflowTypeSubmissionResponse.from(getUseCase.getById(auth.tenantId(), id)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> discard(
      @PathVariable String id, @AuthenticationPrincipal ApiAuthentication auth) {
    discardUseCase.discard(
        auth.tenantId(), id, auth.userId(), "PLATFORM_ADMIN".equals(auth.role()));
    return ResponseEntity.noContent().build();
  }

  private static DraftConfigs draftConfigsOrEmpty(DraftConfigs draftConfigs) {
    return draftConfigs != null
        ? draftConfigs
        : new DraftConfigs(null, null, null, null, null, null);
  }
}
