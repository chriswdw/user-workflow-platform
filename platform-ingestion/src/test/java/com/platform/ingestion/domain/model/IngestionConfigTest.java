package com.platform.ingestion.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.domain.model.SourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestionConfigTest {

  private static final List<FieldMapping> NO_FIELD_MAPPINGS = List.of();
  private static final List<String> NO_IDEMPOTENCY_KEY_FIELDS = List.of();

  @Test
  void constructor_explicitFieldStrategyWithNoField_throws() {
    assertThatThrownBy(
            () ->
                new IngestionConfig(
                    "tenant",
                    "TYPE",
                    SourceType.KAFKA,
                    NO_FIELD_MAPPINGS,
                    UnknownColumnPolicy.IGNORE,
                    IdempotencyKeyStrategy.EXPLICIT_FIELD,
                    null,
                    null,
                    "OPEN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("idempotencyExplicitField")
        .hasMessageContaining("EXPLICIT_FIELD");
  }

  @Test
  void constructor_compositeHashWithNoFields_throws() {
    assertThatThrownBy(
            () ->
                new IngestionConfig(
                    "tenant",
                    "TYPE",
                    SourceType.KAFKA,
                    NO_FIELD_MAPPINGS,
                    UnknownColumnPolicy.IGNORE,
                    IdempotencyKeyStrategy.COMPOSITE_HASH,
                    NO_IDEMPOTENCY_KEY_FIELDS,
                    null,
                    "OPEN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("idempotencyKeyFields")
        .hasMessageContaining("COMPOSITE_HASH");
  }

  @Test
  void constructor_explicitFieldStrategyWithField_succeeds() {
    assertThatCode(
            () ->
                new IngestionConfig(
                    "tenant",
                    "TYPE",
                    SourceType.KAFKA,
                    List.of(),
                    UnknownColumnPolicy.IGNORE,
                    IdempotencyKeyStrategy.EXPLICIT_FIELD,
                    null,
                    "trade_id",
                    "OPEN"))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_compositeHashWithFields_succeeds() {
    assertThatCode(
            () ->
                new IngestionConfig(
                    "tenant",
                    "TYPE",
                    SourceType.KAFKA,
                    List.of(),
                    UnknownColumnPolicy.IGNORE,
                    IdempotencyKeyStrategy.COMPOSITE_HASH,
                    List.of("trade_id", "venue"),
                    null,
                    "OPEN"))
        .doesNotThrowAnyException();
  }
}
