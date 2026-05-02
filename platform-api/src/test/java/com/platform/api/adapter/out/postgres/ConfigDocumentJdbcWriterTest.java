package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigDocumentJdbcWriterTest {

    private static final NamedParameterJdbcTemplate jdbc =
            new NamedParameterJdbcTemplate(EmbeddedPostgresProvider.DATA_SOURCE);

    private final ConfigDocumentJdbcWriter writer =
            new ConfigDocumentJdbcWriter(jdbc, new ObjectMapper());

    @BeforeEach
    void truncate() {
        jdbc.update("TRUNCATE config_documents", Map.of());
    }

    @Test
    void saveAll_persistsDocumentsToDatabase() {
        List<ConfigDocument> docs = List.of(
                doc("doc-1", "tenant-A", "SETTLEMENT_EXCEPTION", ConfigType.WORKFLOW_TYPE_DEFINITION),
                doc("doc-2", "tenant-A", "SETTLEMENT_EXCEPTION", ConfigType.BLOTTER_CONFIG)
        );

        writer.saveAll(docs);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM config_documents WHERE tenant_id = :tenantId",
                new MapSqlParameterSource("tenantId", "tenant-A"),
                Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void saveAll_emptyList_isNoOp() {
        writer.saveAll(List.of());

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM config_documents",
                Map.of(),
                Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void saveAll_serializationFailure_throwsIllegalStateException() {
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonGenerationException("simulated failure", (com.fasterxml.jackson.core.JsonGenerator) null);
            }
        };
        ConfigDocumentJdbcWriter failingWriter = new ConfigDocumentJdbcWriter(jdbc, failingMapper);

        assertThatThrownBy(() -> failingWriter.saveAll(List.of(
                doc("doc-err", "tenant-A", "SETTLEMENT_EXCEPTION", ConfigType.ROUTING_CONFIG)
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialise config document content to JSONB");
    }

    private static ConfigDocument doc(String id, String tenantId, String workflowType, ConfigType type) {
        return new ConfigDocument(id, tenantId, workflowType, type,
                Map.of("key", "value"), "v1", true);
    }
}
