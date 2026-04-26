package com.platform.api.adapter.in.rest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Issues JWTs without credentials — for local development only.
 * Disable in production by setting api.dev.token.enabled=false.
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "api.dev.token.enabled", havingValue = "true", matchIfMissing = true)
public class DevTokenController {

    private final SecretKey signingKey;

    public DevTokenController(@Value("${api.jwt.secret}") String base64Secret) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> issueToken(@RequestBody Map<String, String> body) {
        String userId   = body.getOrDefault("userId",   "dev-user");
        String role     = body.getOrDefault("role",     "ANALYST");
        String tenantId = body.getOrDefault("tenantId", "tenant-1");

        String token = Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .claim("tenantId", tenantId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 8 * 3_600_000L))
                .signWith(signingKey)
                .compact();

        return ResponseEntity.ok(Map.of("token", token));
    }
}
