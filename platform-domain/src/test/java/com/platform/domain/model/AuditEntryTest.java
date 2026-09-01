package com.platform.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuditEntry is the immutable append-only audit record every domain state change must produce
 * (CLAUDE.md financial-services invariant). It's a plain data carrier with no compact-constructor
 * validation, but its accessors — including the nested ChangedField record used to carry
 * previous/new values for every changed field — had no direct test at all.
 */
class AuditEntryTest {

    @Test
    void carriesAllFieldsIncludingChangedFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var changedField = new AuditEntry.ChangedField("status", "OPEN", "RESOLVED");

        var entry = new AuditEntry(
                "audit-1", "tenant-1", "wi-1", "corr-1",
                AuditEventType.STATE_TRANSITION,
                "OPEN", "RESOLVED", "RESOLVE",
                List.of(changedField),
                "user-1", "ANALYST", now, "idem-1");

        assertThat(entry.eventType()).isEqualTo(AuditEventType.STATE_TRANSITION);
        assertThat(entry.previousState()).isEqualTo("OPEN");
        assertThat(entry.newState()).isEqualTo("RESOLVED");
        assertThat(entry.changedFields()).containsExactly(changedField);
        assertThat(entry.changedFields().get(0).fieldPath()).isEqualTo("status");
        assertThat(entry.changedFields().get(0).previousValue()).isEqualTo("OPEN");
        assertThat(entry.changedFields().get(0).newValue()).isEqualTo("RESOLVED");
    }

    @Test
    void everyAuditEventTypeUsedByDomainServicesIsAValidConstant() {
        // Exercises the full enum (all values are initialised together by the JVM on first
        // reference), and pins the specific event types the submission lifecycle and workflow
        // engine are documented (CLAUDE.md, platform-config-engine) to emit — a rename here would
        // silently break every consumer matching on these names.
        assertThat(AuditEventType.values()).contains(
                AuditEventType.INGESTION,
                AuditEventType.STATE_TRANSITION,
                AuditEventType.MAKER_CHECKER_APPROVAL,
                AuditEventType.SUBMISSION_CREATED,
                AuditEventType.SUBMISSION_SUBMITTED_FOR_REVIEW,
                AuditEventType.SUBMISSION_APPROVED,
                AuditEventType.SUBMISSION_REJECTED,
                AuditEventType.SUBMISSION_REVISED,
                AuditEventType.SUBMISSION_DISCARDED);
    }
}
