package com.platform.api.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.domain.model.SourceType;
import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;
import com.platform.ingestion.domain.ports.in.IIngestRecordUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;

import java.util.Map;

public class WorkItemKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkItemKafkaConsumer.class);

    private final IIngestRecordUseCase ingestUseCase;
    private final ObjectMapper objectMapper;

    public WorkItemKafkaConsumer(IIngestRecordUseCase ingestUseCase, ObjectMapper objectMapper) {
        this.ingestUseCase = ingestUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${platform.ingestion.kafka.topic:work-items.ingest}",
            containerFactory = "ingestionKafkaListenerContainerFactory"
    )
    public void handle(@Payload String payload) throws JsonProcessingException {
        RawInboundRecord record = parsePayload(payload);
        IngestionResult result = ingestUseCase.ingest(record);
        switch (result) {
            case IngestionResult.Created c ->
                log.info("workItemId={} tenantId={} workflowType={} msg=ingested",
                        c.workItem().id(), record.tenantId(), record.workflowType());
            case IngestionResult.Duplicate d ->
                log.debug("idempotencyKey={} tenantId={} workflowType={} msg=duplicate_discarded",
                        d.idempotencyKey(), record.tenantId(), record.workflowType());
            case IngestionResult.Rejected r ->
                throw new IngestionRejectionException(
                        "tenantId=" + record.tenantId() + " workflowType=" + record.workflowType()
                        + " reason=" + r.reason());
        }
    }

    @SuppressWarnings("unchecked")
    private RawInboundRecord parsePayload(String payload) throws JsonProcessingException {
        Map<String, Object> map = objectMapper.readValue(payload, new TypeReference<>() {});
        Map<String, String> rawFields = (Map<String, String>) map.getOrDefault("rawFields", Map.of());
        return new RawInboundRecord(
                (String) map.get("tenantId"),
                (String) map.get("workflowType"),
                SourceType.KAFKA,
                (String) map.get("sourceRef"),
                rawFields,
                (String) map.get("makerUserId")
        );
    }
}
