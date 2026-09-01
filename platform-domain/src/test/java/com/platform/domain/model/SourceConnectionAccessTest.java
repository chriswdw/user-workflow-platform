package com.platform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * SourceConnectionAccess records which tenant was granted access to a shared source connection, and
 * by whom — the audit trail for cross-tenant connection sharing. Plain data carrier, no validation
 * logic, but previously had no direct test.
 */
class SourceConnectionAccessTest {

  @Test
  void carriesGrantDetails() {
    OffsetDateTime grantedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    var access = new SourceConnectionAccess("access-1", "conn-1", "tenant-2", "admin-1", grantedAt);

    assertThat(access.sourceConnectionId()).isEqualTo("conn-1");
    assertThat(access.tenantId()).isEqualTo("tenant-2");
    assertThat(access.grantedBy()).isEqualTo("admin-1");
    assertThat(access.grantedAt()).isEqualTo(grantedAt);
  }
}
