package com.platform.ingestion.steps;

import com.platform.domain.model.AuditEventType;
import com.platform.domain.model.SourceType;
import com.platform.domain.shared.FieldPathResolver;
import com.platform.ingestion.domain.model.FieldMapping;
import com.platform.ingestion.domain.model.IdempotencyKeyStrategy;
import com.platform.ingestion.domain.model.IngestionConfig;
import com.platform.ingestion.domain.model.IngestionResult;
import com.platform.ingestion.domain.model.RawInboundRecord;
import com.platform.ingestion.domain.model.UnknownColumnPolicy;
import com.platform.ingestion.domain.ports.in.IIngestRecordUseCase;
import com.platform.ingestion.domain.service.IngestionService;
import com.platform.ingestion.doubles.InMemoryIdempotencyKeyRepository;
import com.platform.ingestion.doubles.InMemoryIngestionAuditRepository;
import com.platform.ingestion.doubles.InMemoryIngestionConfigRepository;
import com.platform.ingestion.doubles.InMemoryIngestionWorkItemRepository;
import com.platform.ingestion.doubles.StubGroupAssignmentPort;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions for the ingestion feature.
 * Cucumber creates a new instance per scenario — all fields are per-scenario state.
 * Domain service wired with in-memory doubles; no Spring context, no database.
 */
public class IngestionStepDefinitions {

    private final InMemoryIngestionConfigRepository configRepo = new InMemoryIngestionConfigRepository();
    private final InMemoryIdempotencyKeyRepository idempotencyRepo = new InMemoryIdempotencyKeyRepository();
    private final InMemoryIngestionWorkItemRepository workItemRepo = new InMemoryIngestionWorkItemRepository();
    private final InMemoryIngestionAuditRepository auditRepo = new InMemoryIngestionAuditRepository();
    private final StubGroupAssignmentPort groupPort = new StubGroupAssignmentPort();
    private final IIngestRecordUseCase ingestionService =
            new IngestionService(configRepo, idempotencyRepo, workItemRepo, auditRepo, groupPort);

    private String tenantId = "tenant-1";
    private IngestionResult result;

    // ── Given ────────────────────────────────────────────────────────────────

    @Given("an ingestion config for tenant {string} workflow {string} source {string} policy {string} with mappings:")
    public void ingestionConfig(String tenant, String workflow, String source, String policy, DataTable dataTable) {
        this.tenantId = tenant;
        saveConfig(tenant, workflow, SourceType.valueOf(source), dataTable, UnknownColumnPolicy.valueOf(policy));
    }

    @Given("the routing port assigns group {string} for workflow type {string}")
    public void routingPortAssignsGroup(String groupId, String workflowType) {
        groupPort.configure(workflowType, groupId);
    }

    @Given("a record with idempotency key {string} has already been ingested for workflow {string}")
    public void recordAlreadyIngested(String idempotencyKey, String workflowType) {
        idempotencyRepo.save(tenantId, workflowType, idempotencyKey);
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("a {string} record is ingested for workflow {string} with fields:")
    public void recordIngested(String sourceType, String workflowType, DataTable dataTable) {
        Map<String, String> fields = dataTable.asMap();
        result = ingestionService.ingest(new RawInboundRecord(
                tenantId, workflowType, SourceType.valueOf(sourceType), "ref-001", fields, "user-1"));
    }

    // ── Then ─────────────────────────────────────────────────────────────────

    @Then("the ingestion result is Created")
    public void theIngestionResultIsCreated() {
        assertThat(result).as("ingestion result").isInstanceOf(IngestionResult.Created.class);
    }

    @Then("the ingestion result is Duplicate")
    public void theIngestionResultIsDuplicate() {
        assertThat(result).as("ingestion result").isInstanceOf(IngestionResult.Duplicate.class);
    }

    @Then("the ingestion result is Rejected with reason mentioning {string}")
    public void theIngestionResultIsRejectedWithReason(String keyword) {
        assertThat(result).as("ingestion result").isInstanceOf(IngestionResult.Rejected.class);
        String reason = ((IngestionResult.Rejected) result).reason();
        assertThat(reason).as("rejection reason").contains(keyword);
    }

    @Then("the work item has field {string} equal to {string}")
    public void theWorkItemHasFieldEqualTo(String dotPath, String expected) {
        var workItem = ((IngestionResult.Created) result).workItem();
        Object value = FieldPathResolver.resolve(workItem.fields(), dotPath).orElse(null);
        assertThat(value).as("field " + dotPath).isEqualTo(expected);
    }

    @Then("the work item is assigned to group {string}")
    public void theWorkItemIsAssignedToGroup(String expectedGroup) {
        var workItem = ((IngestionResult.Created) result).workItem();
        assertThat(workItem.assignedGroup()).as("assigned group").isEqualTo(expectedGroup);
    }

    @Then("the work item source type is {string}")
    public void theWorkItemSourceTypeIs(String expectedSourceType) {
        var workItem = ((IngestionResult.Created) result).workItem();
        assertThat(workItem.source()).as("source type").isEqualTo(SourceType.valueOf(expectedSourceType));
    }

    @Then("the work item fields do not contain key {string}")
    public void theWorkItemFieldsDoNotContainKey(String key) {
        var workItem = ((IngestionResult.Created) result).workItem();
        assertThat(workItem.fields()).as("work item fields").doesNotContainKey(key);
    }

    @Then("an INGESTION audit entry is written")
    public void anIngestionAuditEntryIsWritten() {
        assertThat(auditRepo.all()).anyMatch(e -> e.eventType() == AuditEventType.INGESTION);
    }

    @Then("a DUPLICATE_INGESTION_DISCARDED audit entry is written")
    public void aDuplicateIngestionDiscardedAuditEntryIsWritten() {
        assertThat(auditRepo.all())
                .anyMatch(e -> e.eventType() == AuditEventType.DUPLICATE_INGESTION_DISCARDED);
    }

    @Then("no new work item is saved")
    public void noNewWorkItemIsSaved() {
        assertThat(workItemRepo.all()).as("saved work items").isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void saveConfig(String tenant, String workflowType, SourceType sourceType,
                             DataTable dataTable, UnknownColumnPolicy policy) {
        List<FieldMapping> mappings = dataTable.asMaps().stream()
                .map(row -> new FieldMapping(
                        row.get("sourceField"),
                        row.get("targetField"),
                        Boolean.parseBoolean(row.get("required"))))
                .toList();

        String explicitField = mappings.stream()
                .filter(FieldMapping::required)
                .map(FieldMapping::sourceField)
                .findFirst()
                .orElse(mappings.isEmpty() ? "id" : mappings.get(0).sourceField());

        configRepo.save(new IngestionConfig(
                tenant, workflowType, sourceType, mappings, policy,
                IdempotencyKeyStrategy.EXPLICIT_FIELD,
                List.of(), explicitField,
                "UNDER_REVIEW"
        ));
    }
}
