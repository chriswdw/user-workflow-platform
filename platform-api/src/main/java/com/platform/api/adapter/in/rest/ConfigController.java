package com.platform.api.adapter.in.rest;

import com.platform.api.config.ApiAuthentication;
import com.platform.config.domain.exception.ConfigNotFoundException;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.ports.in.ILoadConfigUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/configs")
public class ConfigController {

    private final ILoadConfigUseCase loadConfig;

    public ConfigController(ILoadConfigUseCase loadConfig) {
        this.loadConfig = loadConfig;
    }

    @GetMapping("/detail-view/{workflowType}")
    public ResponseEntity<Map<String, Object>> getDetailViewConfig(
            @PathVariable String workflowType,
            @AuthenticationPrincipal ApiAuthentication auth) {
        try {
            var doc = loadConfig.loadActive(auth.tenantId(), workflowType, ConfigType.DETAIL_VIEW_CONFIG);
            return ResponseEntity.ok(doc.content());
        } catch (ConfigNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
