package com.platform.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The role claim must surface as a Spring Security authority so declarative authz can use it. */
class ApiAuthenticationTest {

  @Test
  void roleIsExposedAsPrefixedAuthority() {
    var auth = new ApiAuthentication("user-1", "PLATFORM_ADMIN", "tenant-1");

    assertThat(auth.getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_PLATFORM_ADMIN");
  }

  @Test
  void nullRoleYieldsNoAuthorities() {
    var auth = new ApiAuthentication("user-1", null, "tenant-1");

    assertThat(auth.getAuthorities()).isEmpty();
  }
}
