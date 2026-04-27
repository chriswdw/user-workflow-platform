package com.platform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.api.adapter.out.postgres.AuditEntryJdbcRepository;
import com.platform.api.adapter.out.postgres.ConfigDocumentJdbcRepository;
import com.platform.api.adapter.out.postgres.WorkflowConfigJdbcRepository;
import com.platform.api.adapter.out.postgres.WorkItemJdbcRepository;
import com.platform.audit.domain.service.AuditService;
import com.platform.config.domain.service.ConfigService;
import com.platform.workflow.domain.service.WorkflowService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Activated only when spring.datasource.url is configured.
 * Each bean displaces the corresponding @ConditionalOnMissingBean fallback in DevConfig.
 */
@Configuration
@ConditionalOnProperty(name = "spring.datasource.url")
public class PostgresAdapterConfig {

    @Bean
    public WorkItemJdbcRepository workItemJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new WorkItemJdbcRepository(jdbc, objectMapper);
    }

    @Bean
    public AuditEntryJdbcRepository auditEntryJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new AuditEntryJdbcRepository(jdbc, objectMapper);
    }

    @Bean
    public ConfigDocumentJdbcRepository configDocumentJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new ConfigDocumentJdbcRepository(jdbc, objectMapper);
    }

    @Bean
    public WorkflowConfigJdbcRepository workflowConfigJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new WorkflowConfigJdbcRepository(jdbc, objectMapper);
    }

    @Bean
    public AuditService auditService(AuditEntryJdbcRepository auditRepo) {
        return new AuditService(auditRepo);
    }

    @Bean
    public ConfigService configService(ConfigDocumentJdbcRepository configRepo) {
        return new ConfigService(configRepo);
    }

    @Bean
    public WorkflowService workflowService(WorkItemJdbcRepository workItemRepo,
                                            WorkflowConfigJdbcRepository workflowConfigRepo,
                                            AuditEntryJdbcRepository auditRepo) {
        return new WorkflowService(workItemRepo, workflowConfigRepo, auditRepo);
    }
}
