package com.platform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.api.adapter.out.postgres.AuditEntryJdbcRepository;
import com.platform.api.adapter.out.postgres.IdempotencyKeyJdbcRepository;
import com.platform.api.adapter.out.postgres.IngestionConfigJdbcRepository;
import com.platform.api.adapter.out.postgres.IngestionWorkItemJdbcRepository;
import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;
import com.platform.ingestion.domain.ports.in.IIngestRecordUseCase;
import com.platform.ingestion.domain.service.IngestionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
    IGroupAssignmentPort groupAssignmentPort(
            @Value("${platform.ingestion.default-group:group-ops}") String defaultGroup) {
        return (tenantId, workflowType, fields) ->
                new IGroupAssignmentPort.AssignmentResult(defaultGroup, true);
    }

    @Bean
    IIngestRecordUseCase ingestRecordUseCase(
            IngestionConfigJdbcRepository configRepo,
            IdempotencyKeyJdbcRepository idempotencyRepo,
            IngestionWorkItemJdbcRepository workItemRepo,
            AuditEntryJdbcRepository auditRepo,
            IGroupAssignmentPort groupAssignmentPort) {
        return new IngestionService(configRepo, idempotencyRepo, workItemRepo, auditRepo, groupAssignmentPort);
    }
}
