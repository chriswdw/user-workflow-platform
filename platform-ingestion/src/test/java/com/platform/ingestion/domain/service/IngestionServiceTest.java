package com.platform.ingestion.domain.service;

import com.platform.domain.model.AuditEventType;
import com.platform.domain.model.SourceType;
import com.platform.ingestion.domain.exception.IngestionConfigNotFoundException;
import com.platform.ingestion.domain.model.FieldMapping;
import com.platform.ingestion.domain.model.IdempotencyKeyStrategy;
import com.platform.ingestion.domain.model.IngestionConfig;
import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;
import com.platform.ingestion.domain.model.UnknownColumnPolicy;
import com.platform.ingestion.doubles.InMemoryIdempotencyKeyRepository;
import com.platform.ingestion.doubles.InMemoryIngestionAuditRepository;
import com.platform.ingestion.doubles.InMemoryIngestionConfigRepository;
import com.platform.ingestion.doubles.InMemoryIngestionWorkItemRepository;
import com.platform.ingestion.doubles.StubGroupAssignmentPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String WORKFLOW_TYPE = "TRADE_BREAK";

    private final InMemoryIngestionConfigRepository configRepo = new InMemoryIngestionConfigRepository();
    private final InMemoryIdempotencyKeyRepository idempotencyRepo = new InMemoryIdempotencyKeyRepository();
    private final InMemoryIngestionWorkItemRepository workItemRepo = new InMemoryIngestionWorkItemRepository();
    private final InMemoryIngestionAuditRepository auditRepo = new InMemoryIngestionAuditRepository();
    private final StubGroupAssignmentPort groupPort = new StubGroupAssignmentPort();
    private final IngestionService service = new IngestionService(
            configRepo, idempotencyRepo, workItemRepo, auditRepo, groupPort,
            event -> {}, new SimpleMeterRegistry());

    @BeforeEach
    void setUp() {
        groupPort.configure(WORKFLOW_TYPE, "group-ops");
        configRepo.save(new IngestionConfig(
                TENANT, WORKFLOW_TYPE, SourceType.KAFKA,
                List.of(
                        new FieldMapping("tradeRef", "trade.ref", true),
                        new FieldMapping("amount", "trade.amount", false)
                ),
                UnknownColumnPolicy.IGNORE,
                IdempotencyKeyStrategy.EXPLICIT_FIELD,
                null, "tradeRef", "OPEN"));
    }

    @Test
    void ingest_newRecord_returnsCreated() {
        IngestionResult result = service.ingest(inboundRecord(Map.of("tradeRef", "TRD-001", "amount", "1000")));

        assertThat(result).isInstanceOf(IngestionResult.Created.class);
        assertThat(((IngestionResult.Created) result).workItem().status()).isEqualTo("OPEN");
    }

    @Test
    void ingest_newRecord_savesWorkItem() {
        service.ingest(inboundRecord(Map.of("tradeRef", "TRD-001")));

        assertThat(workItemRepo.all()).hasSize(1);
    }

    @Test
    void ingest_newRecord_recordsIngestionAuditEntry() {
        service.ingest(inboundRecord(Map.of("tradeRef", "TRD-001")));

        assertThat(auditRepo.all())
                .anyMatch(e -> e.eventType() == AuditEventType.INGESTION);
    }

    @Test
    void ingest_duplicateIdempotencyKey_returnsDuplicate() {
        service.ingest(inboundRecord(Map.of("tradeRef", "TRD-001")));
        IngestionResult result = service.ingest(inboundRecord(Map.of("tradeRef", "TRD-001")));

        assertThat(result).isInstanceOf(IngestionResult.Duplicate.class);
    }

    @Test
    void ingest_duplicateIdempotencyKey_recordsDuplicateAuditEntry() {
        service.ingest(inboundRecord(Map.of("tradeRef", "TRD-001")));
        service.ingest(inboundRecord(Map.of("tradeRef", "TRD-001")));

        assertThat(auditRepo.all())
                .anyMatch(e -> e.eventType() == AuditEventType.DUPLICATE_INGESTION_DISCARDED);
    }

    @Test
    void ingest_missingRequiredField_returnsRejected() {
        IngestionResult result = service.ingest(inboundRecord(Map.of("amount", "1000")));

        assertThat(result).isInstanceOf(IngestionResult.Rejected.class);
        assertThat(((IngestionResult.Rejected) result).reason()).contains("tradeRef");
    }

    @Test
    void ingest_unknownColumnWithIgnorePolicy_succeeds() {
        IngestionResult result = service.ingest(inboundRecord(Map.of("tradeRef", "TRD-002", "unknown", "value")));

        assertThat(result).isInstanceOf(IngestionResult.Created.class);
    }

    @Test
    void ingest_unknownColumnWithRejectPolicy_returnsRejected() {
        configRepo.save(new IngestionConfig(
                TENANT, WORKFLOW_TYPE, SourceType.KAFKA,
                List.of(new FieldMapping("tradeRef", "trade.ref", true)),
                UnknownColumnPolicy.REJECT,
                IdempotencyKeyStrategy.EXPLICIT_FIELD,
                null, "tradeRef", "OPEN"));

        IngestionResult result = service.ingest(inboundRecord(Map.of("tradeRef", "TRD-003", "unknown", "value")));

        assertThat(result).isInstanceOf(IngestionResult.Rejected.class);
    }

    @Test
    void ingest_noConfigForSourceType_throwsIngestionConfigNotFoundException() {
        RawInboundRecord dbRecord = new RawInboundRecord(
                TENANT, WORKFLOW_TYPE, SourceType.DB_POLL, "src", Map.of("tradeRef", "TRD-X"), "user");

        assertThatThrownBy(() -> service.ingest(dbRecord))
                .isInstanceOf(IngestionConfigNotFoundException.class)
                .hasMessageContaining(WORKFLOW_TYPE)
                .hasMessageContaining("DB_POLL");
    }

    @Test
    void ingest_compositeHashStrategy_deduplicatesCorrectly() {
        configRepo.save(new IngestionConfig(
                TENANT, WORKFLOW_TYPE, SourceType.KAFKA,
                List.of(new FieldMapping("tradeRef", "trade.ref", true)),
                UnknownColumnPolicy.IGNORE,
                IdempotencyKeyStrategy.COMPOSITE_HASH,
                List.of("tradeRef", "amount"), null, "OPEN"));

        service.ingest(inboundRecord(Map.of("tradeRef", "TRD-004", "amount", "500")));
        IngestionResult dup = service.ingest(inboundRecord(Map.of("tradeRef", "TRD-004", "amount", "500")));

        assertThat(dup).isInstanceOf(IngestionResult.Duplicate.class);
    }

    @Test
    void ingest_fieldsAreMappedToNestedStructure() {
        service.ingest(inboundRecord(Map.of("tradeRef", "TRD-005", "amount", "9999")));

        @SuppressWarnings("unchecked")
        Map<String, Object> trade = (Map<String, Object>) workItemRepo.all().get(0).fields().get("trade");
        assertThat(trade)
                .containsEntry("ref", "TRD-005")
                .containsEntry("amount", "9999");
    }

    private RawInboundRecord inboundRecord(Map<String, String> fields) {
        return new RawInboundRecord(TENANT, WORKFLOW_TYPE, SourceType.KAFKA, "src-1", fields, "maker-1");
    }
}
