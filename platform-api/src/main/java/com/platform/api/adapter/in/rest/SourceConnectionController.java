package com.platform.api.adapter.in.rest;

import com.platform.api.config.ApiAuthentication;
import com.platform.config.domain.ports.in.IListSourceConnectionsUseCase;
import com.platform.config.domain.ports.in.IManageSourceConnectionsUseCase;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SourceConnectionController {

  private final IManageSourceConnectionsUseCase manageUseCase;
  private final IListSourceConnectionsUseCase listUseCase;

  public SourceConnectionController(
      IManageSourceConnectionsUseCase manageUseCase, IListSourceConnectionsUseCase listUseCase) {
    this.manageUseCase = manageUseCase;
    this.listUseCase = listUseCase;
  }

  // ── Admin endpoints (/api/v1/admin/source-connections) ────────────────────

  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  @PostMapping("/api/v1/admin/source-connections")
  public ResponseEntity<SourceConnection> adminCreate(
      @RequestBody SourceConnectionRequest body, @AuthenticationPrincipal ApiAuthentication auth) {
    SourceConnection created =
        manageUseCase.create(
            new SourceConnection(
                null,
                body.name(),
                body.displayName(),
                body.parsedConnectionType(),
                body.toConnectionConfig(),
                body.credentialsRef(),
                auth.userId(),
                null,
                null));
    return ResponseEntity.status(201).body(created);
  }

  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  @GetMapping("/api/v1/admin/source-connections")
  public ResponseEntity<List<SourceConnection>> adminListAll(
      @AuthenticationPrincipal ApiAuthentication auth) {
    return ResponseEntity.ok(listUseCase.listAll());
  }

  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  @PatchMapping("/api/v1/admin/source-connections/{id}")
  public ResponseEntity<SourceConnection> adminUpdate(
      @PathVariable String id,
      @RequestBody SourceConnectionRequest body,
      @AuthenticationPrincipal ApiAuthentication auth) {
    SourceConnection updated =
        manageUseCase.update(
            new SourceConnection(
                id,
                body.name(),
                body.displayName(),
                body.parsedConnectionType(),
                body.toConnectionConfig(),
                body.credentialsRef(),
                null,
                null,
                null));
    return ResponseEntity.ok(updated);
  }

  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  @DeleteMapping("/api/v1/admin/source-connections/{id}")
  public ResponseEntity<Void> adminDelete(
      @PathVariable String id, @AuthenticationPrincipal ApiAuthentication auth) {
    manageUseCase.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  @PostMapping("/api/v1/admin/source-connections/{id}/access")
  public ResponseEntity<Void> adminGrantAccess(
      @PathVariable String id,
      @RequestBody Map<String, Object> body,
      @AuthenticationPrincipal ApiAuthentication auth) {
    manageUseCase.grantAccess(id, (String) body.get("tenantId"), auth.userId());
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  @DeleteMapping("/api/v1/admin/source-connections/{id}/access/{tenantId}")
  public ResponseEntity<Void> adminRevokeAccess(
      @PathVariable String id,
      @PathVariable String tenantId,
      @AuthenticationPrincipal ApiAuthentication auth) {
    manageUseCase.revokeAccess(id, tenantId);
    return ResponseEntity.noContent().build();
  }

  // ── Analyst endpoint (/api/v1/source-connections) ─────────────────────────

  @GetMapping("/api/v1/source-connections")
  public ResponseEntity<List<SourceConnection>> listAccessible(
      @RequestParam(required = false) String type,
      @AuthenticationPrincipal ApiAuthentication auth) {
    ConnectionType connectionType = type != null ? ConnectionType.valueOf(type) : null;
    return ResponseEntity.ok(listUseCase.listAccessible(auth.tenantId(), connectionType));
  }
}
