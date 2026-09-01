package com.platform.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * FieldPathResolver is the shared dot-notation path resolver used by the routing rule evaluator,
 * the blotter/detail-view field renderer (via masking), and the workflow transition validator to
 * read arbitrary-depth values out of WorkItem.fields. It previously had zero test coverage even
 * though every one of those consumers depends on its exact edge-case behaviour.
 */
class FieldPathResolverTest {

  @Test
  void resolvesSingleLevelPath() {
    Map<String, Object> fields = Map.of("status", "OPEN");

    assertThat(FieldPathResolver.resolve(fields, "status")).contains("OPEN");
  }

  @Test
  void resolvesMultiLevelNestedPath() {
    Map<String, Object> fields =
        Map.of("trade", Map.of("notionalAmount", Map.of("amount", "1000000")));

    assertThat(FieldPathResolver.resolve(fields, "trade.notionalAmount.amount"))
        .contains("1000000");
  }

  @Test
  void missingTopLevelKeyReturnsEmpty() {
    Map<String, Object> fields = Map.of("status", "OPEN");

    assertThat(FieldPathResolver.resolve(fields, "counterparty")).isEqualTo(Optional.empty());
  }

  @Test
  void missingIntermediateKeyReturnsEmpty() {
    Map<String, Object> fields = Map.of("trade", Map.of("ref", "TRD-001"));

    assertThat(FieldPathResolver.resolve(fields, "trade.notionalAmount.amount"))
        .isEqualTo(Optional.empty());
  }

  @Test
  void pathThroughNonMapIntermediateValueReturnsEmpty() {
    // "trade" resolves to a String, not a nested Map — the next segment can't be traversed.
    Map<String, Object> fields = Map.of("trade", "TRD-001");

    assertThat(FieldPathResolver.resolve(fields, "trade.ref")).isEqualTo(Optional.empty());
  }

  @Test
  void emptyFieldsMapReturnsEmpty() {
    assertThat(FieldPathResolver.resolve(Map.of(), "trade.ref")).isEqualTo(Optional.empty());
  }

  @Test
  void resolvesValueThatIsItselfAMap() {
    // Resolving a path that lands exactly on an intermediate node returns that node whole,
    // rather than requiring the caller to know it's "not a leaf".
    Map<String, Object> nested = Map.of("amount", "1000000", "currency", "USD");
    Map<String, Object> fields = Map.of("trade", Map.of("notionalAmount", nested));

    assertThat(FieldPathResolver.resolve(fields, "trade.notionalAmount")).contains(nested);
  }

  @Test
  void deeplyNestedPathBeyondTwoLevelsResolvesCorrectly() {
    Map<String, Object> fields = Map.of("a", Map.of("b", Map.of("c", Map.of("d", "leaf-value"))));

    assertThat(FieldPathResolver.resolve(fields, "a.b.c.d")).contains("leaf-value");
  }
}
