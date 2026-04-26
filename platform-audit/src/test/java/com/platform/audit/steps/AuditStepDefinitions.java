package com.platform.audit.steps;

import com.platform.audit.domain.model.AuditQuery;
import com.platform.audit.domain.ports.in.IAppendAuditEntryUseCase;
import com.platform.audit.domain.ports.in.IQueryAuditTrailUseCase;
import com.platform.audit.domain.service.AuditService;
import com.platform.audit.doubles.InMemoryAuditEntryRepository;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AuditStepDefinitions {

    private final InMemoryAuditEntryRepository repository = new InMemoryAuditEntryRepository();
    private final AuditService auditService = new AuditService(repository);

    private String tenantId;
    private List<AuditEntry> queryResult;

    // ── Given ────────────────────────────────────────────────────────────────

    @Given("tenant {string}")
    public void tenant(String tenantId) {
        this.tenantId = tenantId;
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("an {word} audit entry is appended for work item {string}")
    public void entryAppended(String eventType, String workItemId) {
        auditService.append(entry(workItemId, AuditEventType.valueOf(eventType), Instant.now()));
    }

    @When("an {word} audit entry is appended for work item {string} at {string}")
    public void entryAppendedAt(String eventType, String workItemId, String timestamp) {
        auditService.append(entry(workItemId, AuditEventType.valueOf(eventType), Instant.parse(timestamp)));
    }

    @When("a {word} audit entry is appended for work item {string}")
    public void aEntryAppended(String eventType, String workItemId) {
        auditService.append(entry(workItemId, AuditEventType.valueOf(eventType), Instant.now()));
    }

    @When("a {word} audit entry is appended for work item {string} at {string}")
    public void aEntryAppendedAt(String eventType, String workItemId, String timestamp) {
        auditService.append(entry(workItemId, AuditEventType.valueOf(eventType), Instant.parse(timestamp)));
    }

    @When("the audit trail for work item {string} is queried")
    public void trailQueried(String workItemId) {
        queryResult = auditService.query(new AuditQuery(tenantId, workItemId, null, null, null));
    }

    @When("the audit trail for work item {string} is queried filtering by event type {string}")
    public void trailQueriedByEventType(String workItemId, String eventType) {
        queryResult = auditService.query(new AuditQuery(
                tenantId, workItemId, List.of(AuditEventType.valueOf(eventType)), null, null));
    }

    @When("the audit trail for work item {string} is queried from {string} to {string}")
    public void trailQueriedByRange(String workItemId, String from, String to) {
        queryResult = auditService.query(new AuditQuery(
                tenantId, workItemId, null, Instant.parse(from), Instant.parse(to)));
    }

    // ── Then ─────────────────────────────────────────────────────────────────

    @Then("the audit trail for work item {string} contains {int} entry")
    @Then("the audit trail for work item {string} contains {int} entries")
    public void trailContainsEntries(String workItemId, int count) {
        queryResult = auditService.query(new AuditQuery(tenantId, workItemId, null, null, null));
        assertThat(queryResult).as("audit trail for " + workItemId).hasSize(count);
    }

    @Then("the trail contains {int} entry")
    public void trailContains(int count) {
        assertThat(queryResult).as("filtered trail").hasSize(count);
    }

    @Then("the trail is empty")
    public void trailIsEmpty() {
        assertThat(queryResult).as("audit trail").isEmpty();
    }

    @Then("the entry has event type {string}")
    public void entryHasEventType(String eventType) {
        assertThat(queryResult).as("trail entries").hasSize(1);
        assertThat(queryResult.get(0).eventType()).isEqualTo(AuditEventType.valueOf(eventType));
    }

    @Then("the entries are in timestamp order")
    public void entriesAreInTimestampOrder() {
        List<Instant> timestamps = queryResult.stream().map(AuditEntry::timestamp).toList();
        for (int i = 1; i < timestamps.size(); i++) {
            assertThat(timestamps.get(i)).as("entry %d timestamp", i)
                    .isAfterOrEqualTo(timestamps.get(i - 1));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AuditEntry entry(String workItemId, AuditEventType eventType, Instant timestamp) {
        return new AuditEntry(
                UUID.randomUUID().toString(),
                tenantId,
                workItemId,
                UUID.randomUUID().toString(),
                eventType,
                null, null, null,
                List.of(),
                "user-1",
                "ANALYST",
                timestamp,
                null
        );
    }
}
