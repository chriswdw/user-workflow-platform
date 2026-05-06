package com.platform.ingestion.domain.model;

import com.platform.domain.model.SourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionConfigTest {

    @Test
    void constructor_explicitFieldStrategyWithNoField_throws() {
        assertThatThrownBy(() -> new IngestionConfig(
                "tenant", "TYPE", SourceType.KAFKA, List.of(),
                UnknownColumnPolicy.IGNORE, IdempotencyKeyStrategy.EXPLICIT_FIELD,
                null, null, "OPEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyExplicitField")
                .hasMessageContaining("EXPLICIT_FIELD");
    }

    @Test
    void constructor_compositeHashWithNoFields_throws() {
        assertThatThrownBy(() -> new IngestionConfig(
                "tenant", "TYPE", SourceType.KAFKA, List.of(),
                UnknownColumnPolicy.IGNORE, IdempotencyKeyStrategy.COMPOSITE_HASH,
                List.of(), null, "OPEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKeyFields")
                .hasMessageContaining("COMPOSITE_HASH");
    }

    @Test
    void constructor_explicitFieldStrategyWithField_succeeds() {
        new IngestionConfig(
                "tenant", "TYPE", SourceType.KAFKA, List.of(),
                UnknownColumnPolicy.IGNORE, IdempotencyKeyStrategy.EXPLICIT_FIELD,
                null, "trade_id", "OPEN");
    }

    @Test
    void constructor_compositeHashWithFields_succeeds() {
        new IngestionConfig(
                "tenant", "TYPE", SourceType.KAFKA, List.of(),
                UnknownColumnPolicy.IGNORE, IdempotencyKeyStrategy.COMPOSITE_HASH,
                List.of("trade_id", "venue"), null, "OPEN");
    }
}
