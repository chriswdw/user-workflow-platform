package com.platform.api.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * DevTokenController issues local-development JWTs without credentials. No Spring context needed —
 * it's constructor-injected with the raw secret and has no other dependencies.
 */
class DevTokenControllerTest {

  // 32 bytes — the minimum HS256 requires.
  private static final String VALID_SECRET =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  @Test
  void issueTokenUsesDefaultsWhenBodyFieldsAreAbsent() {
    var controller = new DevTokenController(VALID_SECRET);

    ResponseEntity<Map<String, String>> response = controller.issueToken(Map.of());

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).containsKey("token");
    assertThat(response.getBody().get("token")).isNotBlank();
  }

  @Test
  void issueTokenHonoursProvidedClaims() {
    var controller = new DevTokenController(VALID_SECRET);

    ResponseEntity<Map<String, String>> response =
        controller.issueToken(
            Map.of("userId", "alice", "role", "SUPERVISOR", "tenantId", "tenant-9"));

    assertThat(response.getBody().get("token")).isNotBlank();
  }

  @Test
  void issuedTokenCarriesTheRequestedClaimsAndAnEightHourLifetime() throws Exception {
    var controller = new DevTokenController(VALID_SECRET);

    String token =
        controller
            .issueToken(Map.of("userId", "alice", "role", "SUPERVISOR", "tenantId", "tenant-9"))
            .getBody()
            .get("token");
    JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

    assertThat(claims.getSubject()).isEqualTo("alice");
    assertThat(claims.getStringClaim("role")).isEqualTo("SUPERVISOR");
    assertThat(claims.getStringClaim("tenantId")).isEqualTo("tenant-9");
    assertThat(claims.getIssueTime()).isNotNull();
    assertThat(claims.getExpirationTime().toInstant())
        .isEqualTo(claims.getIssueTime().toInstant().plus(Duration.ofHours(8)));
  }

  @Test
  void issueTokenThrowsIllegalStateWhenSecretIsTooShortForHs256() {
    // HS256 requires a >=256-bit (32-byte) key; MACSigner rejects anything shorter with a
    // JOSEException, which this controller wraps as IllegalStateException — a real
    // misconfiguration this fail-fast behaviour is meant to surface clearly in dev.
    String tooShortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());
    var controller = new DevTokenController(tooShortSecret);

    assertThatThrownBy(() -> controller.issueToken(Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to sign dev token");
  }
}
