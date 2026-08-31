package com.platform.api.adapter.in.kafka;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.platform.domain.model.SourceType;
import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;
import com.platform.ingestion.domain.ports.in.IIngestRecordUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class WorkItemKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkItemKafkaConsumer.class);
    public static final String TENANT_ID = "tenantId";

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
    public void handle(@Payload String payload, @Headers MessageHeaders headers) {
        String correlationId = extractHeader(headers, "X-Correlation-ID");
        if (correlationId != null) MDC.put("correlationId", correlationId);
        try {
            RawInboundRecord inboundRecord = parsePayload(payload);
            MDC.put(TENANT_ID, inboundRecord.tenantId());
            IngestionResult result = ingestUseCase.ingest(inboundRecord);
            switch (result) {
                case IngestionResult.Created(var workItem) ->
                    log.info("workItemId={} tenantId={} workflowType={} msg=ingested",
                            workItem.id(), inboundRecord.tenantId(), inboundRecord.workflowType());
                case IngestionResult.Duplicate(var idempotencyKey) ->
                    log.debug("idempotencyKey={} tenantId={} workflowType={} msg=duplicate_discarded",
                            idempotencyKey, inboundRecord.tenantId(), inboundRecord.workflowType());
                case IngestionResult.Rejected(var reason) ->
                    throw new IngestionRejectionException(
                            "tenantId=" + inboundRecord.tenantId() + " workflowType=" + inboundRecord.workflowType()
                            + " reason=" + reason);
            }
        } finally {
            MDC.remove("correlationId");
            MDC.remove(TENANT_ID);
        }
    }

    private static String extractHeader(MessageHeaders headers, String name) {
        Object value = headers.get(name);
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        if (value instanceof String s) return s;
        return null;
    }

    @SuppressWarnings("unchecked")
    private RawInboundRecord parsePayload(String payload) {
        Map<String, Object> map = objectMapper.readValue(payload, new TypeReference<>() {});
        Map<String, String> rawFields = (Map<String, String>) map.getOrDefault("rawFields", Map.of());
        return new RawInboundRecord(
                (String) map.get(TENANT_ID),
                (String) map.get("workflowType"),
                SourceType.KAFKA,
                (String) map.get("sourceRef"),
                rawFields,
                (String) map.get("makerUserId")
        );
    }
}
