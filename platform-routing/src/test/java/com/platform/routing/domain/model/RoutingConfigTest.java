package com.platform.routing.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingConfigTest {

    private static final List<RoutingRule> NO_RULES = List.of();

    @Test
    void constructor_nullDefaultGroupId_throws() {
        assertThatThrownBy(() -> new RoutingConfig("id", "tenant", "TYPE", null, false, NO_RULES))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("defaultGroupId");
    }

    @Test
    void constructor_nullTenantId_throws() {
        assertThatThrownBy(() -> new RoutingConfig("id", null, "TYPE", "group-1", false, NO_RULES))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void constructor_valid_succeeds() {
        assertThatCode(() -> new RoutingConfig("id", "tenant", "TYPE", "group-1", false, List.of()))
                .doesNotThrowAnyException();
    }
}
