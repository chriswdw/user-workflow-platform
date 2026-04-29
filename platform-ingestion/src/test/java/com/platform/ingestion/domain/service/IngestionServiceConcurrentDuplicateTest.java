package com.platform.ingestion.domain.service;

import com.platform.domain.model.AuditEventType;
import com.platform.domain.model.SourceType;
import com.platform.ingestion.domain.exception.DuplicateIdempotencyKeyException;
import com.platform.ingestion.domain.model.FieldMapping;
import com.platform.ingestion.domain.model.IdempotencyKeyStrategy;
import com.platform.ingestion.domain.model.IngestionConfig;
import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;
import com.platform.ingestion.domain.model.UnknownColumnPolicy;
import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;
import com.platform.ingestion.doubles.InMemoryIdempotencyKeyRepository;
import com.platform.ingestion.doubles.InMemoryIngestionAuditRepository;
import com.platform.ingestion.doubles.InMemoryIngestionConfigRepository;
import com.platform.ingestion.doubles.StubGroupAssignmentPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the concurrent-duplicate edge case in IngestionService:
 * two threads both pass idempotencyRepository.exists() before either saves,
 * then the second INSERT hits the DB unique constraint and the adapter throws
 * DuplicateIdempotencyKeyException. The service must handle this gracefully.
 */
class IngestionServiceConcurrentDuplicateTest {

    private static final String TENANT        = "tenant-1";
    private static final String WORKFLOW_TYPE = "SETTLEMENT_EXCEPTION";

    @Test
    void ingest_returnsDuplicate_whenWorkItemRepositoryThrowsDuplicateKey() {
        var configRepo   = new InMemoryIngestionConfigRepository();
        var idempotencyRepo = new InMemoryIdempotencyKeyRepository();
        var auditRepo    = new InMemoryIngestionAuditRepository();
        var groupPort    = new StubGroupAssignmentPort();

        configRepo.save(new IngestionConfig(
                TENANT, WORKFLOW_TYPE, SourceType.KAFKA,
                List.of(new FieldMapping("tradeRef", "trade.ref", true)),
                UnknownColumnPolicy.IGNORE,
                IdempotencyKeyStrategy.EXPLICIT_FIELD,
                List.of(), "tradeRef", "UNDER_REVIEW"));
        groupPort.configure(WORKFLOW_TYPE, "group-ops");

        // Work item repository that always throws — simulates concurrent duplicate
        var throwingWorkItemRepo = (com.platform.ingestion.domain.ports.out.IIngestionWorkItemRepository)
                workItem -> { throw new DuplicateIdempotencyKeyException(workItem.idempotencyKey()); };

        var service = new IngestionService(configRepo, idempotencyRepo,
                throwingWorkItemRepo, auditRepo, groupPort);

        RawInboundRecord record = new RawInboundRecord(
                TENANT, WORKFLOW_TYPE, SourceType.KAFKA,
                "src-1", Map.of("tradeRef", "TRD-CONCURRENT"), "system");

        IngestionResult result = service.ingest(record);

        assertThat(result).isInstanceOf(IngestionResult.Duplicate.class);
        assertThat(((IngestionResult.Duplicate) result).idempotencyKey()).isEqualTo("TRD-CONCURRENT");

        // Audit entry must be written for the concurrent duplicate
        assertThat(auditRepo.all()).hasSize(1);
        assertThat(auditRepo.all().get(0).eventType())
                .isEqualTo(AuditEventType.DUPLICATE_INGESTION_DISCARDED);
    }
}
