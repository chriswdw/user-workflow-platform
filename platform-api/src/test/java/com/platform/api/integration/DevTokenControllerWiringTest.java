package com.platform.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.api.adapter.in.rest.DevTokenController;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Asserts when {@link DevTokenController} is registered. The endpoint mints JWTs for any role
 * without credentials, so it must be strictly opt-in: absent unless {@code
 * api.dev.token.enabled=true}, and never present under the {@code prod} profile.
 */
class DevTokenControllerWiringTest {

  private static final String SECRET =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(DevTokenController.class)
          .withPropertyValues("api.jwt.secret=" + SECRET);

  @Test
  void controllerIsAbsentWhenPropertyIsNotSet() {
    runner.run(context -> assertThat(context).doesNotHaveBean(DevTokenController.class));
  }

  @Test
  void controllerIsPresentWhenExplicitlyEnabled() {
    runner
        .withPropertyValues("api.dev.token.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(DevTokenController.class));
  }

  @Test
  void controllerIsAbsentWhenExplicitlyDisabled() {
    runner
        .withPropertyValues("api.dev.token.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(DevTokenController.class));
  }

  @Test
  void controllerIsAbsentUnderProdProfileConfigFile() {
    runner
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.profiles.active=prod")
        .run(context -> assertThat(context).doesNotHaveBean(DevTokenController.class));
  }
}
