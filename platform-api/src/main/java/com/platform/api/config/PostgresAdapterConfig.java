package com.platform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.api.adapter.out.postgres.AuditEntryJdbcRepository;
import com.platform.api.adapter.out.postgres.ConfigDocumentJdbcRepository;
import com.platform.api.adapter.out.postgres.ConfigDocumentJdbcWriter;
import com.platform.api.adapter.out.postgres.SourceConnectionJdbcRepository;
import com.platform.api.adapter.out.postgres.WorkflowConfigJdbcRepository;
import com.platform.api.adapter.out.postgres.WorkflowTypeSubmissionJdbcRepository;
import com.platform.api.adapter.out.postgres.WorkItemJdbcRepository;
import com.platform.audit.domain.service.AuditService;
import com.platform.config.domain.service.ConfigService;
import com.platform.config.domain.service.SourceConnectionService;
import com.platform.config.domain.service.SubmissionCreationService;
import com.platform.config.domain.service.SubmissionDraftService;
import com.platform.config.domain.service.SubmissionLifecycleService;
import com.platform.config.domain.service.SubmissionQueryService;
import com.platform.config.domain.service.SubmissionReviewService;
import com.platform.domain.ports.out.IDomainEventPublisher;
import com.platform.workflow.domain.service.WorkflowService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${platform.config.maker-checker.enabled:true}")
    private boolean makerCheckerEnabled;

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
    public WorkflowTypeSubmissionJdbcRepository workflowTypeSubmissionJdbcRepository(
            NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new WorkflowTypeSubmissionJdbcRepository(jdbc, objectMapper);
    }

    @Bean
    public ConfigDocumentJdbcWriter configDocumentJdbcWriter(
            NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new ConfigDocumentJdbcWriter(jdbc, objectMapper);
    }

    @Bean
    public SourceConnectionJdbcRepository sourceConnectionJdbcRepository(
            NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new SourceConnectionJdbcRepository(jdbc, objectMapper);
    }

    @Bean
    public AuditService auditService(AuditEntryJdbcRepository auditRepo) {
        return new AuditService(auditRepo);
    }

    @Bean
    public ConfigService configService(ConfigDocumentJdbcRepository configRepo, MeterRegistry meterRegistry) {
        return new ConfigService(configRepo, meterRegistry);
    }

    @Bean
    public WorkflowService workflowService(WorkItemJdbcRepository workItemRepo,
                                            WorkflowConfigJdbcRepository workflowConfigRepo,
                                            AuditEntryJdbcRepository auditRepo,
                                            IDomainEventPublisher eventPublisher,
                                            MeterRegistry meterRegistry) {
        return new WorkflowService(workItemRepo, workflowConfigRepo, auditRepo, eventPublisher, meterRegistry);
    }

    @Bean
    public SubmissionCreationService submissionCreationService(
            WorkflowTypeSubmissionJdbcRepository repo,
            ConfigDocumentJdbcWriter writer,
            AuditEntryJdbcRepository auditRepo) {
        return new SubmissionCreationService(repo, writer, auditRepo, makerCheckerEnabled);
    }

    @Bean
    public SubmissionDraftService submissionDraftService(
            WorkflowTypeSubmissionJdbcRepository repo,
            AuditEntryJdbcRepository auditRepo) {
        return new SubmissionDraftService(repo, auditRepo);
    }

    @Bean
    public SubmissionLifecycleService submissionLifecycleService(
            WorkflowTypeSubmissionJdbcRepository repo,
            AuditEntryJdbcRepository auditRepo) {
        return new SubmissionLifecycleService(repo, auditRepo);
    }

    @Bean
    public SubmissionReviewService submissionReviewService(
            WorkflowTypeSubmissionJdbcRepository repo,
            ConfigDocumentJdbcWriter writer,
            AuditEntryJdbcRepository auditRepo) {
        return new SubmissionReviewService(repo, writer, auditRepo);
    }

    @Bean
    public SubmissionQueryService submissionQueryService(
            WorkflowTypeSubmissionJdbcRepository repo) {
        return new SubmissionQueryService(repo);
    }

    @Bean
    public SourceConnectionService sourceConnectionService(SourceConnectionJdbcRepository repo) {
        return new SourceConnectionService(repo);
    }
}
