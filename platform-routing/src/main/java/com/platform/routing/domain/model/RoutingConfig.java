package com.platform.routing.domain.model;

import java.util.List;
import java.util.Objects;

public record RoutingConfig(
    String id,
    String tenantId,
    String workflowType,
    String defaultGroupId,
    boolean alertOnDefault,
    List<RoutingRule> rules) {
  public RoutingConfig {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(workflowType, "workflowType must not be null");
    Objects.requireNonNull(
        defaultGroupId,
        "defaultGroupId must not be null — every routing config must have a fallback group");
    Objects.requireNonNull(rules, "rules must not be null");
  }
}
