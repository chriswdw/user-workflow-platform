package com.platform.api.adapter.in.rest;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues JWTs without credentials, for any requested role — for local development only. Strictly
 * opt-in: the bean exists only when api.dev.token.enabled=true, which is set in the local and test
 * profiles and must never be set in any production configuration.
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "api.dev.token.enabled", havingValue = "true", matchIfMissing = false)
public class DevTokenController {

  private final byte[] secretKeyBytes;

  public DevTokenController(@Value("${api.jwt.secret}") String base64Secret) {
    this.secretKeyBytes = Base64.getDecoder().decode(base64Secret);
  }

  @PostMapping("/token")
  public ResponseEntity<Map<String, String>> issueToken(@RequestBody Map<String, String> body) {
    String userId = body.getOrDefault("userId", "dev-user");
    String role = body.getOrDefault("role", "ANALYST");
    String tenantId = body.getOrDefault("tenantId", "tenant-1");

    String token = buildToken(userId, role, tenantId);

    return ResponseEntity.ok(Map.of("token", token));
  }

  private String buildToken(String userId, String role, String tenantId) {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId)
            .claim("role", role)
            .claim("tenantId", tenantId)
            .issueTime(new Date())
            .expirationTime(new Date(System.currentTimeMillis() + 8 * 3_600_000L))
            .build();

    SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    try {
      signedJwt.sign(new MACSigner(secretKeyBytes));
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to sign dev token", e);
    }
    return signedJwt.serialize();
  }
}
