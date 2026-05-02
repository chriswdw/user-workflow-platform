package com.platform.api.steps;

import com.platform.api.doubles.InMemoryAuditQueryPort;
import com.platform.api.doubles.InMemoryConfigPort;
import com.platform.api.doubles.InMemorySourceConnectionPort;
import com.platform.api.doubles.InMemorySubmissionPort;
import com.platform.api.doubles.InMemoryTransitionPort;
import com.platform.api.doubles.InMemoryWorkItemQueryPort;
import com.platform.config.domain.model.ConfigDocument;
import com.platform.config.domain.model.ConfigType;
import com.platform.config.domain.model.DraftConfigs;
import com.platform.config.domain.model.SubmissionStatus;
import com.platform.config.domain.model.WorkflowTypeSubmission;
import com.platform.config.domain.ports.in.CreateSubmissionCommand;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
import com.platform.domain.model.ConnectionType;
import com.platform.domain.model.SourceConnection;
import com.platform.domain.model.SourceType;
import com.platform.domain.model.WorkItem;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ApiStepDefinitions {

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryWorkItemQueryPort workItemStore;
    @Autowired private InMemoryTransitionPort transitionPort;
    @Autowired private InMemoryAuditQueryPort auditStore;
    @Autowired private InMemoryConfigPort configStore;
    @Autowired private InMemorySubmissionPort submissionStore;
    @Autowired private InMemorySourceConnectionPort sourceConnectionStore;

    @Value("${api.jwt.secret}")
    private String jwtSecret;

    private String authHeader;
    private ResultActions lastResult;

    @Before
    public void reset() {
        authHeader = null;
        lastResult = null;
        submissionStore.reset();
        sourceConnectionStore.reset();
    }

    // ── Given ────────────────────────────────────────────────────────────────

    @Given("the system has a work item {string} for tenant {string} in state {string}")
    public void systemHasWorkItem(String id, String tenantId, String state) {
        WorkItem item = new WorkItem(
                id, tenantId, "SETTLEMENT_EXCEPTION", UUID.randomUUID().toString(),
                null, SourceType.KAFKA, "src-ref", "idem-key",
                state, "group-ops", false, Map.of(),
                null, null, null, null, null,
                1, "system", Instant.now(), Instant.now()
        );
        workItemStore.save(item);
    }

    @Given("the workflow allows transition {string} from {string} to {string}")
    public void workflowAllowsTransition(String transitionName, String fromState, String toState) {
        transitionPort.allowTransition(transitionName, toState);
    }

    @Given("I am authenticated as user {string} with role {string} for tenant {string}")
    public void authenticatedAs(String userId, String role, String tenantId) {
        authHeader = "Bearer " + buildJwt(userId, role, tenantId);
    }

    @Given("I am not authenticated")
    public void notAuthenticated() {
        authHeader = null;
    }

    @Given("a detail view config exists for workflow type {string} and tenant {string}")
    public void detailViewConfigExists(String workflowType, String tenantId) {
        configStore.save(new ConfigDocument(
                "test-dvc-" + workflowType, tenantId, workflowType,
                ConfigType.DETAIL_VIEW_CONFIG,
                Map.of("sections", List.of(), "actions", List.of()),
                "1", true));
    }

    @Given("the audit trail for work item {string} has {int} entries")
    public void auditTrailHasEntries(String workItemId, int count) {
        List<AuditEntry> entries = buildAuditEntries("tenant-1", workItemId, count);
        auditStore.setTrail("tenant-1", workItemId, entries);
    }

    // ── Given — submissions ───────────────────────────────────────────────────

    @Given("a submission for workflow type {string} exists for tenant {string}")
    public void submissionExistsForWorkflowType(String workflowType, String tenantId) {
        submissionStore.create(new CreateSubmissionCommand(
                tenantId, "alice", workflowType, workflowType + " Display", null, incompleteDraftConfigs()));
    }

    @Given("a draft submission {string} exists for tenant {string} workflow type {string} submitted by {string}")
    public void draftSubmissionExists(String id, String tenantId, String workflowType, String submittedBy) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        submissionStore.seed(new WorkflowTypeSubmission(
                id, tenantId, workflowType, workflowType + " Display", null,
                SubmissionStatus.DRAFT, "Draft",
                incompleteDraftConfigs(),
                submittedBy, null, null, null, null,
                1, 1, now, now));
    }

    @Given("a complete draft submission {string} exists for tenant {string} workflow type {string} submitted by {string}")
    public void completeDraftSubmissionExists(String id, String tenantId, String workflowType, String submittedBy) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        submissionStore.seed(new WorkflowTypeSubmission(
                id, tenantId, workflowType, workflowType + " Display", null,
                SubmissionStatus.DRAFT, "Draft",
                completeDraftConfigs(),
                submittedBy, null, null, null, null,
                1, 1, now, now));
    }

    @Given("a pending submission {string} exists for tenant {string} workflow type {string} submitted by {string}")
    public void pendingSubmissionExists(String id, String tenantId, String workflowType, String submittedBy) {
        completeDraftSubmissionExists(id, tenantId, workflowType, submittedBy);
        submissionStore.submit(tenantId, id, submittedBy);
    }

    @Given("a rejected submission {string} exists for tenant {string} workflow type {string} submitted by {string}")
    public void rejectedSubmissionExists(String id, String tenantId, String workflowType, String submittedBy) {
        pendingSubmissionExists(id, tenantId, workflowType, submittedBy);
        submissionStore.reject(tenantId, id, "bob", "Test rejection");
    }

    // ── Given — source connections ────────────────────────────────────────────

    @Given("a source connection {string} of type {string} exists")
    public void sourceConnectionExists(String id, String type) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        sourceConnectionStore.putConnection(new SourceConnection(
                id, id, id + " Display",
                ConnectionType.valueOf(type),
                Map.of(), null, "admin", now, now));
    }

    @Given("tenant {string} has access to source connection {string}")
    public void tenantHasAccessToConnection(String tenantId, String connectionId) {
        sourceConnectionStore.grantAccessForTest(connectionId, tenantId);
    }

    // ── When ─────────────────────────────────────────────────────────────────

    @When("^I GET (.+)$")
    public void iGet(String path) throws Exception {
        var req = get(path).accept(MediaType.APPLICATION_JSON);
        if (authHeader != null) req = req.header("Authorization", authHeader);
        lastResult = mockMvc.perform(req);
    }

    @When("^I POST (.+) with body (.+)$")
    public void iPost(String path, String body) throws Exception {
        var req = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .accept(MediaType.APPLICATION_JSON);
        if (authHeader != null) req = req.header("Authorization", authHeader);
        lastResult = mockMvc.perform(req);
    }

    @When("^I PATCH (.+) with body (.+)$")
    public void iPatch(String path, String body) throws Exception {
        var req = patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .accept(MediaType.APPLICATION_JSON);
        if (authHeader != null) req = req.header("Authorization", authHeader);
        lastResult = mockMvc.perform(req);
    }

    @When("^I DELETE (.+)$")
    public void iDelete(String path) throws Exception {
        var req = delete(path).accept(MediaType.APPLICATION_JSON);
        if (authHeader != null) req = req.header("Authorization", authHeader);
        lastResult = mockMvc.perform(req);
    }

    // ── Then ─────────────────────────────────────────────────────────────────

    @Then("the response status is {int}")
    public void responseStatusIs(int status) throws Exception {
        lastResult.andExpect(status().is(status));
    }

    @Then("the response contains work item id {string}")
    public void responseContainsWorkItemId(String id) throws Exception {
        lastResult.andExpect(jsonPath("$.id").value(id));
    }

    @Then("the response contains work item status {string}")
    public void responseContainsWorkItemStatus(String status) throws Exception {
        lastResult.andExpect(jsonPath("$.status").value(status));
    }

    @Then("the response contains {int} audit entries")
    public void responseContainsAuditEntries(int count) throws Exception {
        lastResult.andExpect(jsonPath("$.length()").value(count));
    }

    @Then("the response is a non-empty JSON array")
    public void responseIsNonEmptyArray() throws Exception {
        lastResult.andExpect(jsonPath("$").isArray())
                  .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Then("the response contains a {string} field")
    public void responseContainsField(String fieldName) throws Exception {
        lastResult.andExpect(jsonPath("$." + fieldName).exists());
    }

    @Then("the response contains a JWT token")
    public void responseContainsJwtToken() throws Exception {
        lastResult.andExpect(jsonPath("$.token").isString())
                  .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
    }

    @Then("the response contains statusCode {string}")
    public void responseContainsStatusCode(String statusCode) throws Exception {
        lastResult.andExpect(jsonPath("$.statusCode").value(statusCode));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildJwt(String userId, String role, String tenantId) {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .claim("tenantId", tenantId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    private List<AuditEntry> buildAuditEntries(String tenantId, String workItemId, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new AuditEntry(
                        UUID.randomUUID().toString(), tenantId, workItemId,
                        UUID.randomUUID().toString(), AuditEventType.INGESTION,
                        null, null, null, List.of(), "user-1", "ANALYST",
                        Instant.now(), null))
                .toList();
    }

    private static DraftConfigs incompleteDraftConfigs() {
        Map<String, Object> basic = Map.of("workflowType", "TRADE_BREAK");
        return new DraftConfigs(basic, basic, basic, basic, null, null);
    }

    private static DraftConfigs completeDraftConfigs() {
        Map<String, Object> basic = Map.of("workflowType", "TRADE_BREAK");
        Map<String, Object> blotter = Map.of("columns", List.of(Map.of("field", "trade.ref", "header", "Ref")));
        Map<String, Object> detail = Map.of("sections", List.of(
                Map.of("title", "Details", "fields", List.of(Map.of("field", "trade.ref")))));
        return new DraftConfigs(basic, basic, basic, basic, blotter, detail);
    }
}
