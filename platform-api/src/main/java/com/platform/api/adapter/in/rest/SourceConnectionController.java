package com.platform.api.adapter.in.rest;

import com.platform.api.config.ApiAuthentication;
import com.platform.config.domain.ports.in.IListSourceConnectionsUseCase;
import com.platform.config.domain.ports.in.IManageSourceConnectionsUseCase;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SourceConnectionController {

    private static final String FIELD_CONNECTION_TYPE = "connectionType";
    private static final String FIELD_CONFIG = "config";

    private final IManageSourceConnectionsUseCase manageUseCase;
    private final IListSourceConnectionsUseCase listUseCase;

    public SourceConnectionController(IManageSourceConnectionsUseCase manageUseCase,
                                       IListSourceConnectionsUseCase listUseCase) {
        this.manageUseCase = manageUseCase;
        this.listUseCase = listUseCase;
    }

    // ── Admin endpoints (/api/v1/admin/source-connections) ────────────────────

    @PostMapping("/api/v1/admin/source-connections")
    @SuppressWarnings("unchecked")
    public ResponseEntity<SourceConnection> adminCreate(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ApiAuthentication auth) {
        if (!isPlatformAdmin(auth)) return ResponseEntity.status(403).build();
        SourceConnection created = manageUseCase.create(new SourceConnection(
                null,
                (String) body.get("name"),
                (String) body.get("displayName"),
                ConnectionType.valueOf((String) body.get(FIELD_CONNECTION_TYPE)),
                body.containsKey(FIELD_CONFIG) ? (Map<String, Object>) body.get(FIELD_CONFIG) : Map.of(),
                (String) body.get("credentialsRef"),
                auth.userId(),
                null, null));
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/api/v1/admin/source-connections")
    public ResponseEntity<List<SourceConnection>> adminListAll(
            @AuthenticationPrincipal ApiAuthentication auth) {
        if (!isPlatformAdmin(auth)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(listUseCase.listAll());
    }

    @PatchMapping("/api/v1/admin/source-connections/{id}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<SourceConnection> adminUpdate(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ApiAuthentication auth) {
        if (!isPlatformAdmin(auth)) return ResponseEntity.status(403).build();
        ConnectionType connectionType = body.containsKey(FIELD_CONNECTION_TYPE)
                ? ConnectionType.valueOf((String) body.get(FIELD_CONNECTION_TYPE))
                : null;
        SourceConnection updated = manageUseCase.update(new SourceConnection(
                id,
                (String) body.get("name"),
                (String) body.get("displayName"),
                connectionType,
                body.containsKey(FIELD_CONFIG) ? (Map<String, Object>) body.get(FIELD_CONFIG) : null,
                (String) body.get("credentialsRef"),
                null, null, null));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/api/v1/admin/source-connections/{id}")
    public ResponseEntity<Void> adminDelete(
            @PathVariable String id,
            @AuthenticationPrincipal ApiAuthentication auth) {
        if (!isPlatformAdmin(auth)) return ResponseEntity.status(403).build();
        manageUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/admin/source-connections/{id}/access")
    public ResponseEntity<Void> adminGrantAccess(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal ApiAuthentication auth) {
        if (!isPlatformAdmin(auth)) return ResponseEntity.status(403).build();
        manageUseCase.grantAccess(id, (String) body.get("tenantId"), auth.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/admin/source-connections/{id}/access/{tenantId}")
    public ResponseEntity<Void> adminRevokeAccess(
            @PathVariable String id,
            @PathVariable String tenantId,
            @AuthenticationPrincipal ApiAuthentication auth) {
        if (!isPlatformAdmin(auth)) return ResponseEntity.status(403).build();
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

    private static boolean isPlatformAdmin(ApiAuthentication auth) {
        return "PLATFORM_ADMIN".equals(auth.role());
    }
}
