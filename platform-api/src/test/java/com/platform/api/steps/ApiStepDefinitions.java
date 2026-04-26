package com.platform.api.steps;

import com.platform.api.doubles.InMemoryAuditQueryPort;
import com.platform.api.doubles.InMemoryTransitionPort;
import com.platform.api.doubles.InMemoryWorkItemQueryPort;
import com.platform.domain.model.AuditEntry;
import com.platform.domain.model.AuditEventType;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ApiStepDefinitions {

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryWorkItemQueryPort workItemStore;
    @Autowired private InMemoryTransitionPort transitionPort;
    @Autowired private InMemoryAuditQueryPort auditStore;

    @Value("${api.jwt.secret}")
    private String jwtSecret;

    private String authHeader;
    private ResultActions lastResult;

    @Before
    public void reset() {
        authHeader = null;
        lastResult = null;
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

    @Given("the audit trail for work item {string} has {int} entries")
    public void auditTrailHasEntries(String workItemId, int count) {
        List<AuditEntry> entries = buildAuditEntries("tenant-1", workItemId, count);
        auditStore.setTrail("tenant-1", workItemId, entries);
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
}
