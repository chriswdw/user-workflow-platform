package com.platform.api.adapter.in.rest;

import com.platform.audit.domain.model.AuditQuery;
import com.platform.audit.domain.ports.in.IQueryAuditTrailUseCase;
import com.platform.api.config.ApiAuthentication;
import com.platform.domain.model.AuditEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final IQueryAuditTrailUseCase queryAuditTrail;

    public AuditController(IQueryAuditTrailUseCase queryAuditTrail) {
        this.queryAuditTrail = queryAuditTrail;
    }

    @GetMapping("/work-items/{id}")
    public ResponseEntity<List<AuditEntry>> getAuditTrail(@PathVariable String id,
                                                            @AuthenticationPrincipal ApiAuthentication auth) {
        List<AuditEntry> trail = queryAuditTrail.query(
                new AuditQuery(auth.tenantId(), id, null, null, null));
        return ResponseEntity.ok(trail);
    }
}
