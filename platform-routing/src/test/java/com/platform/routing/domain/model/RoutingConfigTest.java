package com.platform.routing.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingConfigTest {

    @Test
    void constructor_nullDefaultGroupId_throws() {
        assertThatThrownBy(() -> new RoutingConfig("id", "tenant", "TYPE", null, false, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("defaultGroupId");
    }

    @Test
    void constructor_nullTenantId_throws() {
        assertThatThrownBy(() -> new RoutingConfig("id", null, "TYPE", "group-1", false, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void constructor_valid_succeeds() {
        new RoutingConfig("id", "tenant", "TYPE", "group-1", false, List.of());
    }
}
