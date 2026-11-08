package com.platform.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.api.adapter.out.postgres.AuditEntryJdbcRepository;
import com.platform.api.adapter.out.postgres.EmbeddedPostgresProvider;
import com.platform.api.adapter.out.routing.RoutingGroupAssignmentAdapter;
import com.platform.api.config.IngestionAdapterConfig;
import com.platform.domain.ports.out.IDomainEventPublisher;
import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;
import com.platform.routing.domain.ports.in.IRouteWorkItemUseCase;
import com.platform.routing.domain.service.RoutingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The production {@link IngestionAdapterConfig} must hand ingestion the real routing engine, not a
 * hardcoded group, and must register nothing when no datasource is configured.
 */
class IngestionAdapterConfigWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(IngestionAdapterConfig.class)
          .withBean(
              NamedParameterJdbcTemplate.class,
              () -> new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE))
          .withBean(ObjectMapper.class, JsonMapper::new)
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withBean(IDomainEventPublisher.class, () -> event -> {})
          .withBean(
              AuditEntryJdbcRepository.class,
              () ->
                  new AuditEntryJdbcRepository(
                      new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE),
                      new JsonMapper()));

  @Test
  void groupAssignmentIsBackedByTheRoutingEngine() {
    runner
        .withPropertyValues("spring.datasource.url=jdbc:postgresql://embedded/test")
        .run(context -> {
          assertThat(context).hasSingleBean(IGroupAssignmentPort.class);
          assertThat(context.getBean(IGroupAssignmentPort.class))
              .isInstanceOf(RoutingGroupAssignmentAdapter.class);
          assertThat(context.getBean(IRouteWorkItemUseCase.class))
              .isInstanceOf(RoutingService.class);
        });
  }

  @Test
  void aLeftoverDefaultGroupPropertyNoLongerChangesRouting() {
    runner
        .withPropertyValues(
            "spring.datasource.url=jdbc:postgresql://embedded/test",
            "platform.ingestion.default-group=group-should-be-ignored")
        .run(context ->
            assertThat(context.getBean(IGroupAssignmentPort.class))
                .isInstanceOf(RoutingGroupAssignmentAdapter.class));
  }

  @Test
  void registersNothingWithoutADatasource() {
    runner.run(context -> {
      assertThat(context).doesNotHaveBean(IGroupAssignmentPort.class);
      assertThat(context).doesNotHaveBean(IRouteWorkItemUseCase.class);
    });
  }

  @Test
  void aClockProvidedByTheApplicationTakesPrecedence() {
    Clock fixed = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    runner
        .withPropertyValues("spring.datasource.url=jdbc:postgresql://embedded/test")
        .withBean(Clock.class, () -> fixed)
        .run(context -> assertThat(context.getBean(Clock.class)).isSameAs(fixed));
  }
}
