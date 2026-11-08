package com.platform.api.config;

import com.platform.api.adapter.out.postgres.AuditEntryJdbcRepository;
import com.platform.api.adapter.out.postgres.IdempotencyKeyJdbcRepository;
import com.platform.api.adapter.out.postgres.IngestionConfigJdbcRepository;
import com.platform.api.adapter.out.postgres.IngestionWorkItemJdbcRepository;
import com.platform.api.adapter.out.postgres.RoutingConfigJdbcRepository;
import com.platform.api.adapter.out.routing.RoutingGroupAssignmentAdapter;
import com.platform.domain.ports.out.IDomainEventPublisher;
import com.platform.ingestion.domain.ports.in.IIngestRecordUseCase;
import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;
import com.platform.ingestion.domain.service.IngestionService;
import com.platform.routing.domain.ports.in.IRouteWorkItemUseCase;
import com.platform.routing.domain.service.RoutingService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(name = "spring.datasource.url")
public class IngestionAdapterConfig {

  @Bean
  IngestionConfigJdbcRepository ingestionConfigJdbcRepository(
      NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    return new IngestionConfigJdbcRepository(jdbc, objectMapper);
  }

  @Bean
  IdempotencyKeyJdbcRepository idempotencyKeyJdbcRepository(NamedParameterJdbcTemplate jdbc) {
    return new IdempotencyKeyJdbcRepository(jdbc);
  }

  @Bean
  IngestionWorkItemJdbcRepository ingestionWorkItemJdbcRepository(
      NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    return new IngestionWorkItemJdbcRepository(jdbc, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  RoutingConfigJdbcRepository routingConfigJdbcRepository(
      NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    return new RoutingConfigJdbcRepository(jdbc, objectMapper);
  }

  @Bean
  IRouteWorkItemUseCase routeWorkItemUseCase(
      RoutingConfigJdbcRepository routingConfigRepo,
      AuditEntryJdbcRepository auditRepo,
      MeterRegistry meterRegistry,
      Clock clock) {
    return new RoutingService(routingConfigRepo, auditRepo, meterRegistry, clock);
  }

  /**
   * Group assignment is decided by the tenant's routing config through the routing module. There is
   * deliberately no fallback group here: with no routing config the record fails to the DLQ, and
   * "no rule matched" is answered by the config's own default group.
   */
  @Bean
  IGroupAssignmentPort groupAssignmentPort(IRouteWorkItemUseCase routeWorkItemUseCase) {
    return new RoutingGroupAssignmentAdapter(routeWorkItemUseCase);
  }

  @Bean
  IIngestRecordUseCase ingestRecordUseCase(
      IngestionConfigJdbcRepository configRepo,
      IdempotencyKeyJdbcRepository idempotencyRepo,
      IngestionWorkItemJdbcRepository workItemRepo,
      AuditEntryJdbcRepository auditRepo,
      IGroupAssignmentPort groupAssignmentPort,
      IDomainEventPublisher eventPublisher,
      MeterRegistry meterRegistry) {
    return new IngestionService(
        configRepo,
        idempotencyRepo,
        workItemRepo,
        auditRepo,
        groupAssignmentPort,
        eventPublisher,
        meterRegistry);
  }
}
