package com.platform.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.api.adapter.out.postgres.EmbeddedPostgresProvider;
import com.platform.api.adapter.out.postgres.RoutingConfigJdbcRepository;
import com.platform.routing.domain.model.RoutingConfig;
import com.platform.routing.domain.model.RoutingResult;
import com.platform.routing.domain.model.WorkItemToRoute;
import com.platform.routing.domain.service.RoutingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves the routing config that Liquibase seeds for the demo tenant is readable and routes as
 * documented. It runs against its own freshly migrated database because other tests truncate the
 * shared one.
 */
class SeededRoutingConfigTest {

  private static final DataSource FRESH = EmbeddedPostgresProvider.newMigratedDataSource();
  private static final NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(FRESH);

  private final RoutingConfigJdbcRepository repository =
      new RoutingConfigJdbcRepository(jdbc, new JsonMapper());
  private final RoutingService routing =
      new RoutingService(
          repository,
          entry -> {},
          new SimpleMeterRegistry(),
          Clock.fixed(Instant.parse("2026-11-09T09:00:00Z"), ZoneOffset.UTC));

  @Test
  void theSeededConfigIsReadableAndHasADefaultGroupAndTheHighNotionalRule() {
    RoutingConfig config =
        repository.findActiveByTenantAndWorkflowType("tenant-1", "SETTLEMENT_EXCEPTION").get();

    assertThat(config.defaultGroupId()).isEqualTo("group-ops");
    assertThat(config.rules()).hasSize(1);
    assertThat(config.rules().get(0).id()).isEqualTo("rule-high-notional");
    assertThat(config.rules().get(0).targetGroupId()).isEqualTo("group-senior");
  }

  @Test
  void aHighNotionalTradeIsEscalatedToTheSeniorGroup() {
    RoutingResult result = route("50000000.00");

    assertThat(result.assignedGroupId()).isEqualTo("group-senior");
    assertThat(result.routedByDefault()).isFalse();
  }

  @Test
  void aTradeExactlyAtTheThresholdIsEscalated() {
    assertThat(route("10000000").assignedGroupId()).isEqualTo("group-senior");
  }

  @Test
  void aSmallerTradeGoesToTheDefaultGroup() {
    RoutingResult result = route("175000.00");

    assertThat(result.assignedGroupId()).isEqualTo("group-ops");
    assertThat(result.routedByDefault()).isTrue();
  }

  @Test
  void aTradeWithNoNotionalGoesToTheDefaultGroup() {
    RoutingResult result =
        routing.route(
            new WorkItemToRoute(
                "wi-1",
                "tenant-1",
                "SETTLEMENT_EXCEPTION",
                "system",
                Map.of("trade", Map.of("ref", "T-1"))));

    assertThat(result.assignedGroupId()).isEqualTo("group-ops");
    assertThat(result.routedByDefault()).isTrue();
  }

  private RoutingResult route(String notionalAmount) {
    return routing.route(
        new WorkItemToRoute(
            "wi-1",
            "tenant-1",
            "SETTLEMENT_EXCEPTION",
            "system",
            Map.of(
                "trade",
                Map.of("notionalAmount", Map.of("amount", notionalAmount, "currency", "USD")))));
  }
}
