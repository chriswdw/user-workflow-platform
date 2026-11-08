package com.platform.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.api.config.PostgresAdapterConfig;
import com.platform.api.domain.ports.IFindWorkItemPort;
import com.platform.api.domain.ports.IListWorkItemsPort;
import com.platform.workflow.domain.ports.in.ITransitionWorkItemUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Without a datasource, the production adapter wiring must register nothing. There are no in-memory
 * fallback beans to step in, so a misconfigured deployment fails at startup (missing beans) instead
 * of silently serving fake data.
 */
class NoFallbackWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(PostgresAdapterConfig.class);

  @Test
  void noDatasourceMeansNoWorkItemPortsAreRegistered() {
    runner.run(context -> {
      assertThat(context).doesNotHaveBean(IFindWorkItemPort.class);
      assertThat(context).doesNotHaveBean(IListWorkItemsPort.class);
      assertThat(context).doesNotHaveBean(ITransitionWorkItemUseCase.class);
    });
  }
}
