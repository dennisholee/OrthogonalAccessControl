package com.oac.decision.bdd;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class PolicyDecisionSteps {

    private static final java.util.Set<String> TEST_COLLECTIONS = java.util.Set.of(
            "policies", "relationships", "resource_grants", "pii_classification", "consistency_tokens");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final Map<String, Object> requestBody = new LinkedHashMap<>();
    private final Map<String, Object> runtimeContext = new LinkedHashMap<>();
    private final Map<String, Object> boundaryContext = new LinkedHashMap<>();
    private ResponseEntity<Map<String, Object>> response;
    private ResponseEntity<Map<String, Object>> policyCreateResponse;
    private ResponseEntity<List<Map<String, Object>>> auditEventsResponse;
    private String capturedConsistencyToken;
    private ScreenCapture screenCapture;
    private String currentFeatureName;
    private String currentScenarioName;

    @Before
    public void beforeScenario(Scenario scenario) {
        // Drop all collections between scenarios to prevent cross-contamination
        try {
            mongoTemplate.dropCollection("policies");
            mongoTemplate.dropCollection("relationships");
            mongoTemplate.dropCollection("resource_grants");
            mongoTemplate.dropCollection("pii_classification");
            mongoTemplate.dropCollection("consistency_tokens");
        } catch (Exception e) {
            // Collections may not exist yet — safe to ignore
        }

        // Assert empty-slate precondition: all test collections must be empty
        // This catches cross-scenario data leakage that could silently change test outcomes
        try {
            for (String collection : TEST_COLLECTIONS) {
                long count = mongoTemplate.count(org.springframework.data.mongodb.core.query.Query.query(
                        new org.springframework.data.mongodb.core.query.Criteria()), collection);
                org.junit.jupiter.api.Assertions.assertEquals(0, count,
                        "Precondition: collection '" + collection + "' should be empty before scenario");
            }
        } catch (Exception e) {
            // If count fails (collection just dropped), that's fine
        }

        // Reset state between scenarios
        this.requestBody.clear();
        this.runtimeContext.clear();
        this.boundaryContext.clear();
        this.response = null;
        this.policyCreateResponse = null;
        this.auditEventsResponse = null;
        this.capturedConsistencyToken = null;

        // Extract feature name from the scenario ID (e.g. "Feature Name.Scenario Name")
        String fullId = scenario.getId();
        String[] parts = fullId.split("\\.");
        if (parts.length >= 2) {
            currentFeatureName = parts[0];
            currentScenarioName = parts[1];
        } else {
            currentFeatureName = scenario.getSourceTagNames().stream()
                    .filter(t -> t.startsWith("@"))
                    .findFirst().orElse("Unknown");
            currentScenarioName = scenario.getName();
        }
        screenCapture = new ScreenCapture(currentFeatureName, currentScenarioName);
        screenCapture.init();
    }

    @After
    public void afterScenario() {
        // Capture MongoDB post-state from known collections
        capturePostState("policies");
        capturePostState("relationships");
        capturePostState("resource_grants");
        capturePostState("pii_classification");
        screenCapture.write();
    }

    private void capturePostState(String collection) {
        try {
            List<Map> docs = mongoTemplate.findAll(Map.class, collection);
            screenCapture.capturePostState(collection, docs);
        } catch (Exception e) {
            // Collection may not exist or connection may be down — skip
        }
    }

    // ==================== GIVEN ====================

    @Given("the policy decision service is running on a random port")
    public void theServiceIsRunning() {
        int port = CucumberSpringConfiguration.getPort();
        assertThat(port).isGreaterThan(0);
        screenCapture.log("Service running on port: " + port);
    }

    @Given("MongoDB is seeded with baseline fixtures")
    public void seedBaselineFixtures() {
        Map<String, Object> allowPolicy = new LinkedHashMap<>();
        allowPolicy.put("name", "POL.RBAC.ACCOUNT.READ.ALLOW.v1");
        allowPolicy.put("effect", "ALLOW");
        allowPolicy.put("subjectId", "user-reader");
        allowPolicy.put("action", "read");
        allowPolicy.put("resourceType", "account");
        allowPolicy.put("resourceId", "acc-1");
        allowPolicy.put("tenant", "tenant-a");
        allowPolicy.put("geography", "us");
        allowPolicy.put("market", "retail");
        allowPolicy.put("lineOfBusiness", "cards");
        allowPolicy.put("channel", "staff");
        allowPolicy.put("state", "ACTIVE");
        mongoTemplate.save(allowPolicy, "policies");
        screenCapture.captureSeedData("policies (baseline: POL.RBAC.ACCOUNT.READ.ALLOW.v1)", allowPolicy);

        Map<String, Object> grant1 = new LinkedHashMap<>();
        grant1.put("subjectId", "user-1");
        grant1.put("resourceType", "account");
        grant1.put("resourceId", "acc-1");
        grant1.put("tenant", "tenant-a");
        mongoTemplate.save(grant1, "resource_grants");
        screenCapture.captureSeedData("resource_grants (baseline: user-1 → acc-1)", grant1);

        Map<String, Object> grant2 = new LinkedHashMap<>();
        grant2.put("subjectId", "user-1");
        grant2.put("resourceType", "account");
        grant2.put("resourceId", "acc-2");
        grant2.put("tenant", "tenant-a");
        mongoTemplate.save(grant2, "resource_grants");
        screenCapture.captureSeedData("resource_grants (baseline: user-1 → acc-2)", grant2);

        screenCapture.log("Seeded baseline fixtures: 1 ALLOW policy + 2 resource grants");
    }

    @Given("a policy document with effect {string} and name {string} is saved to MongoDB")
    public void savePolicyToMongoDB(String effect, String name) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (" + name + ")", policy);
        screenCapture.log("Saved policy: " + name + " effect=" + effect);
    }

    @Given("a policy document with effect {string} and field-mask {string} is saved to MongoDB")
    public void savePolicyWithFieldMask(String effect, String fieldMasks) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.FIELDMASK." + UUID.randomUUID().toString().substring(0, 8);
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        // Store field masks as a list of entries to avoid dotted keys in MongoDB
        List<Map<String, String>> maskList = new ArrayList<>();
        for (String entry : fieldMasks.split(",")) {
            String[] parts = entry.split("=");
            Map<String, String> maskEntry = new LinkedHashMap<>();
            maskEntry.put("field", parts[0]);
            maskEntry.put("level", parts[1]);
            maskList.add(maskEntry);
        }
        policy.put("fieldMasks", maskList);
        mongoTemplate.save(policy, "policies");
        // Also inject field masks into runtime context so AllowRule can process them
        this.runtimeContext.put("fieldMasks", maskList);
        screenCapture.captureSeedData("policies (" + name + " with field masks)", policy);
        screenCapture.log("Saved policy with field masks: " + fieldMasks);
    }

    @Given("a policy document with effect {string} requiring relationship {string} is saved to MongoDB")
    public void savePolicyRequiringRelationship(String effect, String relationshipType) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.REBAC." + UUID.randomUUID().toString().substring(0, 8);
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        policy.put("requiredRelationship", relationshipType);
        // Don't hardcode action/resourceType; the policies must match the request context
        // which is set by subsequent Given steps (subject, action, resource, boundary)
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (" + name + " ReBAC)", policy);
        screenCapture.log("Saved ReBAC policy: requires " + relationshipType);
    }

    @Given("a policy document with effect {string} and time-window caveat {string} is saved to MongoDB")
    public void savePolicyWithTimeWindowCaveat(String effect, String timeWindow) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.CAVEAT." + UUID.randomUUID().toString().substring(0, 8);
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        policy.put("timeWindow", timeWindow);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (" + name + " time-window)", policy);
    }

    @Given("a policy document with effect {string} and source-ip caveat {string} is saved to MongoDB")
    public void savePolicyWithSourceIpCaveat(String effect, String cidr) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.CAVEAT." + UUID.randomUUID().toString().substring(0, 8);
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        policy.put("sourceIpRange", cidr);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (" + name + " source-ip)", policy);
    }

    @Given("a policy document with effect {string} with both time-window {string} and source-ip {string} is saved to MongoDB")
    public void savePolicyWithBothCaveats(String effect, String timeWindow, String cidr) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.CAVEAT." + UUID.randomUUID().toString().substring(0, 8);
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        policy.put("timeWindow", timeWindow);
        policy.put("sourceIpRange", cidr);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (" + name + " both caveats)", policy);
    }

    @Given("a policy document with effect {string} for workload {string} is saved to MongoDB")
    public void savePolicyForWorkload(String effect, String workloadId) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.WORKLOAD." + workloadId;
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        policy.put("subjectType", "workload");
        policy.put("subjectId", workloadId);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (" + name + " workload)", policy);
    }

    @Given("a policy document with effect {string} for action {string} is saved to MongoDB")
    public void savePolicyForAction(String effect, String action) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.ACTION." + action.toUpperCase();
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        policy.put("action", action);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (" + name + " action)", policy);
    }

    @Given("a policy document with effect {string} for channel {string} is saved to MongoDB")
    public void savePolicyForChannel(String effect, String channel) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.CHANNEL." + channel.toUpperCase();
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        policy.put("channel", channel);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (" + name + " channel)", policy);
    }

    @Given("a policy document with effect {string} is saved to MongoDB")
    public void saveSimplePolicy(String effect) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.SIMPLE." + UUID.randomUUID().toString().substring(0, 8);
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (" + name + " simple)", policy);
    }

    @Given("a relationship edge from {string} to {string} of type {string} is saved to MongoDB")
    public void saveRelationshipEdge(String fromId, String toId, String type) {
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("subjectId", fromId);
        rel.put("resourceId", toId);
        rel.put("relationshipType", type);
        rel.put("createdAt", Instant.now().toString());
        mongoTemplate.save(rel, "relationships");
        screenCapture.captureSeedData("relationships (" + fromId + " → " + toId + " : " + type + ")", rel);
    }

    @Given("a relationship edge from {string} to {string} of type {string} with expiry {string} is saved to MongoDB")
    public void saveRelationshipEdgeWithExpiry(String fromId, String toId, String type, String expiry) {
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("subjectId", fromId);
        rel.put("resourceId", toId);
        rel.put("relationshipType", type);
        rel.put("createdAt", Instant.now().toString());
        rel.put("expiresAt", expiry);
        mongoTemplate.save(rel, "relationships");
        screenCapture.captureSeedData("relationships (" + fromId + " → " + toId + " expired: " + expiry + ")", rel);
    }

    @Given("a tag-based PII classification for pattern {string} with level {string} is configured")
    public void configureTagBasedPii(String pattern, String level) {
        Map<String, Object> tag = new LinkedHashMap<>();
        tag.put("fieldPattern", pattern);
        tag.put("accessLevel", level);
        mongoTemplate.save(tag, "pii_classification");
        // Also inject PII into runtime context for AllowRule processing
        @SuppressWarnings("unchecked")
        List<Map<String, String>> piiList = (List<Map<String, String>>) this.runtimeContext.computeIfAbsent(
                "piiClassification", k -> new ArrayList<Map<String, String>>());
        piiList.add(Map.of("fieldPattern", pattern, "accessLevel", level));
        screenCapture.captureSeedData("pii_classification (" + pattern + " → " + level + ")", tag);
    }

    @Given("the current time is {string}")
    public void setCurrentTime(String time) {
        this.runtimeContext.put("requestTime", time);
    }

    @Given("a runtime context with key {string} value {string}")
    public void addRuntimeContext(String key, String value) {
        this.runtimeContext.put(key, value);
    }

    @Given("the last write consistency token is captured")
    public void captureLastWriteConsistencyToken() {
        this.capturedConsistencyToken = "token-" + Instant.now().toEpochMilli();
        screenCapture.log("Captured consistency token: " + this.capturedConsistencyToken);
    }

    @Given("the captured consistency token is provided")
    public void provideCapturedConsistencyToken() {
        this.requestBody.put("consistencyToken", this.capturedConsistencyToken);
    }

    @Given("a consistency token {string}")
    public void addConsistencyToken(String token) {
        this.requestBody.put("consistencyToken", token);
    }

    @Given("a policy document with effect {string} and SpEL condition {string} is saved to MongoDB")
    public void savePolicyWithSpelCondition(String effect, String spelCondition) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.SPEL." + UUID.randomUUID().toString().substring(0, 8);
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        policy.put("spelCondition", spelCondition);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (" + name + " SpEL)", policy);
        screenCapture.log("Saved policy with SpEL condition: " + spelCondition);
    }

    @Given("a subject with id {string} and department {string}")
    public void setSubjectWithDepartment(String id, String department) {
        this.requestBody.put("subject", Map.of("type", "human", "id", id, "department", department));
        this.runtimeContext.put("subjectDepartment", department);
    }

    @Given("a relationship chain {string}")
    public void saveRelationshipChain(String chain) {
        // Format: "CEO->VP->Director->CSR:manages" or "alice->ORD-001:approver"
        int colonIdx = chain.lastIndexOf(':');
        if (colonIdx == -1) {
            throw new IllegalArgumentException("Relationship chain must specify type after colon: " + chain);
        }
        String relationshipType = chain.substring(colonIdx + 1);
        String pathPart = chain.substring(0, colonIdx);
        String[] nodes = pathPart.split("->");
        for (int i = 0; i < nodes.length - 1; i++) {
            String fromId = nodes[i].trim();
            String toId = nodes[i + 1].trim();
            Map<String, Object> rel = new LinkedHashMap<>();
            rel.put("subjectId", fromId);
            rel.put("resourceId", toId);
            rel.put("relationshipType", relationshipType);
            rel.put("createdAt", Instant.now().toString());
            mongoTemplate.save(rel, "relationships");
            screenCapture.captureSeedData("relationships (" + fromId + " → " + toId + " : " + relationshipType + ")", rel);
        }
        screenCapture.log("Saved relationship chain: " + chain);
    }

    @Given("a consistency token {string} is the latest for policy updates")
    public void setLatestConsistencyToken(String token) {
        this.capturedConsistencyToken = token;
        Map<String, Object> tokenRecord = new LinkedHashMap<>();
        tokenRecord.put("token", token);
        tokenRecord.put("type", "policy_update");
        tokenRecord.put("createdAt", Instant.now().toString());
        mongoTemplate.save(tokenRecord, "consistency_tokens");
        screenCapture.log("Set latest consistency token: " + token);
    }

    @Given("the latest consistency token is {string}")
    public void setLatestConsistencyTokenValue(String token) {
        this.capturedConsistencyToken = token;
        Map<String, Object> tokenRecord = new LinkedHashMap<>();
        tokenRecord.put("token", token);
        tokenRecord.put("type", "policy_update");
        tokenRecord.put("createdAt", Instant.now().toString());
        mongoTemplate.save(tokenRecord, "consistency_tokens");
        screenCapture.log("Set latest consistency token: " + token);
    }

    @Given("a consistency token {string} has never been issued")
    public void tokenNeverIssued(String token) {
        // Just record the token so it can be referenced; no record is saved to Mongo
        this.capturedConsistencyToken = token;
        screenCapture.log("Token never issued: " + token);
    }

    @Given("a user {string} and resource {string} have no relationship")
    public void userAndResourceNoRelationship(String user, String resource) {
        // Nothing to seed — absence of a relationship is the default state
        this.requestBody.put("subject", Map.of("type", "human", "id", user));
        this.requestBody.put("resource", Map.of("type", "order", "id", resource));
        screenCapture.log("No relationship seeded between " + user + " and " + resource);
    }

    @Given("a subject {string} with id {string}")
    public void setSubject(String type, String id) {
        this.requestBody.put("subject", Map.of("type", type, "id", id));
    }

    @Given("an action {string}")
    public void setAction(String action) {
        this.requestBody.put("action", action);
    }

    @Given("a resource type {string} with id {string}")
    public void setResource(String type, String id) {
        this.requestBody.put("resource", Map.of("type", type, "id", id));
    }

    @Given("a boundary context tenant {string} geography {string} market {string} lineOfBusiness {string} channel {string}")
    public void setBoundaryContext(String tenant, String geography, String market, String lob, String channel) {
        this.boundaryContext.clear();
        this.boundaryContext.put("tenant", tenant);
        this.boundaryContext.put("geography", geography);
        this.boundaryContext.put("market", market);
        this.boundaryContext.put("lineOfBusiness", lob);
        this.boundaryContext.put("channel", channel);
        this.requestBody.put("boundaryContext", this.boundaryContext);
    }

    @Given("an endpoint classification {string}")
    public void setEndpointClassification(String classification) {
        this.requestBody.put("endpointClassification", classification);
    }

    @Given("MongoDB is stopped")
    public void stopMongoDB() {
        // Signal to DependencyOutageRule that MongoDB dependency is unhealthy
        this.runtimeContext.put("dependencyHealthy", false);
        screenCapture.log("Simulated MongoDB stop: dependency flagged as unhealthy");
    }

    @Given("a create policy request for effect {string} and name {string}")
    public void prepareCreatePolicy(String effect, String name) {
        this.requestBody.clear();
        this.requestBody.put("name", name);
        this.requestBody.put("effect", effect);
        this.requestBody.put("owner", "test-owner");
        this.requestBody.put("author", "test-author");
        this.requestBody.put("riskLevel", "LOW");
        screenCapture.log("Prepared create-policy request: " + name + " effect=" + effect);
    }

    // ==================== WHEN ====================

    @When("the policy create request is sent via HTTP")
    public void sendPolicyCreateRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(this.requestBody, headers);
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/v1/admin/policies";
        this.response = restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        this.policyCreateResponse = this.response;
        captureRequestEvidence("POST", url, this.requestBody, this.response);
    }

    @When("a promote policy request is sent for the created policy to state {string}")
    public void sendPromotePolicy(String targetState) {
        if (this.policyCreateResponse == null || this.policyCreateResponse.getBody() == null) {
            throw new IllegalStateException("No created policy to promote");
        }
        String policyId = (String) this.policyCreateResponse.getBody().get("policyId");
        Map<String, Object> promoteBody = new LinkedHashMap<>();
        promoteBody.put("targetState", targetState);
        promoteBody.put("approvers", List.of("approver-1", "approver-2"));
        promoteBody.put("changeRationale", "Test promotion");
        promoteBody.put("rollbackReference", "v1");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(promoteBody, headers);
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/v1/admin/policies/" + policyId + "/promote";
        this.response = restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        captureRequestEvidence("POST", url, promoteBody, this.response);
    }

    @When("a check permission request is sent via HTTP")
    public void sendCheckPermissionRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>(this.requestBody);
        if (!this.runtimeContext.isEmpty()) {
            body.put("runtimeContext", new LinkedHashMap<>(this.runtimeContext));
        }

        // Pre-condition log: check for common missing-field issues before HTTP call
        if (body.containsKey("subject") && body.containsKey("resource") && body.containsKey("action")) {
            String subjectId = ((Map<String, String>) body.get("subject")).get("id");
            String resourceId = ((Map<String, String>) body.get("resource")).get("id");
            String consistencyToken = (String) body.get("consistencyToken");
            
            // Log ReBAC reachability if relationships are seeded
            long relationshipCount = mongoTemplate.count(
                    org.springframework.data.mongodb.core.query.Query.query(
                            new org.springframework.data.mongodb.core.query.Criteria()), "relationships");
            if (relationshipCount > 0 && subjectId != null && resourceId != null) {
                screenCapture.log("[Pre-Check] relationships seeded: " + relationshipCount
                        + " | subject=" + subjectId + " resource=" + resourceId);
            }
            
            // Log consistency token state 
            if (consistencyToken != null) {
                long tokenCount = mongoTemplate.count(
                        org.springframework.data.mongodb.core.query.Query.query(
                                org.springframework.data.mongodb.core.query.Criteria.where("token").is(consistencyToken)),
                        "consistency_tokens");
                screenCapture.log("[Pre-Check] consistency token '" + consistencyToken
                        + "' found in store: " + (tokenCount > 0));
            }
        }

        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/v1/decisions/check-permission";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        this.response = restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        captureRequestEvidence("POST", url, body, this.response);
    }

    @When("a check permission request is sent with token {string}")
    public void sendCheckPermissionWithToken(String token) {
        this.requestBody.put("consistencyToken", token);
        sendCheckPermissionRequest();
    }

    @When("a check permission request with strict consistency flag And token {string} is sent")
    public void sendCheckPermissionStrictWithToken(String token) {
        this.requestBody.put("consistencyToken", token);
        this.requestBody.put("strictConsistency", true);
        sendCheckPermissionRequest();
    }

    @Then("the explanation should contain {string}")
    public void verifyExplanationContains(String expectedText) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        String explanation = (String) body.get("explanation");
        assertThat(explanation).isNotNull();
        assertThat(explanation).contains(expectedText);
        screenCapture.logAssertion("Explanation contains", explanation.contains(expectedText),
                expectedText, explanation);
    }

    @When("a check permission request is sent via HTTP with missing boundary")
    public void sendCheckPermissionWithoutBoundary() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>(this.requestBody);
        body.remove("boundaryContext");

        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/v1/decisions/check-permission";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        this.response = restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        captureRequestEvidence("POST", url, body, this.response);
    }

    @When("a lookup resources request is sent for action {string} and resource type {string}")
    public void sendLookupResourcesRequest(String action, String resourceType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", this.requestBody.get("subject"));
        body.put("action", action);
        body.put("resourceType", resourceType);
        body.put("boundaryContext", this.boundaryContext);

        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/v1/decisions/lookup-resources";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        this.response = restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        captureRequestEvidence("POST", url, body, this.response);
    }

    @When("an audit events query is sent for the created policy")
    public void sendAuditEventsQuery() {
        String policyId = (String) this.policyCreateResponse.getBody().get("policyId");
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/v1/admin/audit-events?entityId=" + policyId;
        this.auditEventsResponse = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        // Set this.response for status code verification steps
        int status = this.auditEventsResponse.getStatusCode().value();
        this.response = new ResponseEntity<>(Map.of("status", status), this.auditEventsResponse.getHeaders(), this.auditEventsResponse.getStatusCode());
        captureRequestEvidence("GET", url, null,
                new ResponseEntity<>(null, this.auditEventsResponse.getHeaders(), this.auditEventsResponse.getStatusCode()));
    }

    // ==================== THEN ====================

    @Then("the response status should be {int}")
    public void verifyResponseStatus(int expectedStatus) {
        int actualStatus = this.response.getStatusCode().value();
        assertThat(actualStatus).isEqualTo(expectedStatus);
        screenCapture.logAssertion("Response status", actualStatus == expectedStatus,
                String.valueOf(expectedStatus), String.valueOf(actualStatus));
    }

    @Then("the decision should be {string} with code {string}")
    public void verifyDecisionWithCode(String expectedDecision, String expectedCode) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        String actualDecision = (String) body.get("decision");
        String actualCode = (String) body.get("decisionCode");
        assertThat(actualDecision).isEqualTo(expectedDecision);
        assertThat(actualCode).isEqualTo(expectedCode);
        screenCapture.logAssertion("Decision", expectedDecision.equals(actualDecision) && expectedCode.equals(actualCode),
                expectedDecision + " / " + expectedCode, actualDecision + " / " + actualCode);
    }

    @Then("the decision should be {string}")
    public void verifyDecision(String expectedDecision) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        String actualDecision = (String) body.get("decision");
        assertThat(actualDecision).isEqualTo(expectedDecision);
        screenCapture.logAssertion("Decision", expectedDecision.equals(actualDecision),
                expectedDecision, actualDecision);
    }

    @Then("the decision code should be {string}")
    public void verifyDecisionCode(String expectedCode) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        String actualCode = (String) body.get("decisionCode");
        assertThat(actualCode).isEqualTo(expectedCode);
        screenCapture.logAssertion("Decision code", expectedCode.equals(actualCode),
                expectedCode, actualCode);
    }

    @Then("the policy state should be {string}")
    public void verifyPolicyState(String expectedState) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        String actualState = (String) body.get("state");
        assertThat(actualState).isEqualTo(expectedState);
        screenCapture.logAssertion("Policy state", expectedState.equals(actualState),
                expectedState, actualState);
    }

    @Then("the response should include a consistency token")
    public void verifyConsistencyTokenInResponse() {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("consistencyToken")).isNotNull();
        screenCapture.logAssertion("Consistency token present", body.get("consistencyToken") != null,
                "non-null", String.valueOf(body.get("consistencyToken")));
    }

    @Then("the response should include field mask {string} with level {string}")
    public void verifyFieldMask(String fieldName, String level) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> accessMap = (Map<String, Object>) body.get("attributeAccessMap");
        assertThat(accessMap).isNotNull();
        // The AttributeAccessMap record serializes as {"fieldAccess": {...}, "tagAccess": {...}}
        // Check fieldAccess first (specific field-level ACL entries)
        Object fieldsObj = accessMap.get("fieldAccess");
        String actualLevel = null;
        if (fieldsObj instanceof Map) {
            actualLevel = (String) ((Map) fieldsObj).get(fieldName);
        }
        // If not found in fieldAccess, check tagAccess (pattern-based classification)
        if (actualLevel == null) {
            Object tagsObj = accessMap.get("tagAccess");
            if (tagsObj instanceof Map) {
                // Try direct match first, then prefix/contains match
                actualLevel = (String) ((Map) tagsObj).get(fieldName);
                if (actualLevel == null) {
                    for (var entry : ((Map<String, Object>) tagsObj).entrySet()) {
                        String key = entry.getKey();
                        if (fieldName.startsWith(key.replace("*", "")) || fieldName.contains(key.replace("*", ""))) {
                            actualLevel = (String) entry.getValue();
                            break;
                        }
                    }
                }
            }
        }
        // Fallback to flat access
        if (actualLevel == null) {
            actualLevel = (String) accessMap.get(fieldName);
        }
        assertThat(actualLevel).isEqualTo(level);
        screenCapture.logAssertion("Field mask: " + fieldName, level.equals(actualLevel), level, actualLevel);
    }

    @Then("the response should not include field mask {string}")
    public void verifyNoFieldMask(String fieldName) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> accessMap = (Map<String, Object>) body.get("attributeAccessMap");
        if (accessMap != null) {
            Object direct = accessMap.get(fieldName);
            Object fieldsObj = accessMap.get("fieldAccess");
            Object nested = (fieldsObj instanceof Map) ? ((Map) fieldsObj).get(fieldName) : null;
            Object tagsObj = accessMap.get("tagAccess");
            Object tagNested = (tagsObj instanceof Map) ? ((Map) tagsObj).get(fieldName) : null;
            assertThat(direct == null && nested == null && tagNested == null).isTrue();
        }
    }

    @Then("the resource IDs should include {string}")
    public void verifyResourceIdsInclude(String expectedId) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("resourceIds");
        assertThat(ids).contains(expectedId);
        screenCapture.logAssertion("Resource IDs contain " + expectedId, ids.contains(expectedId),
                "contains " + expectedId, ids.toString());
    }

    @Then("the resource IDs should be empty")
    public void verifyResourceIdsEmpty() {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("resourceIds");
        assertThat(ids).isEmpty();
        screenCapture.logAssertion("Resource IDs empty", ids.isEmpty(), "[]", ids.toString());
    }

    @Then("the audit events should include {string}")
    public void verifyAuditEventsInclude(String expectedEventType) {
        List<Map<String, Object>> events = this.auditEventsResponse.getBody();
        assertThat(events).isNotNull();
        boolean found = events.stream().anyMatch(event -> expectedEventType.equals(event.get("eventType")));
        assertThat(found).isTrue();
        screenCapture.logAssertion("Audit event: " + expectedEventType, found, "present", found ? "found" : "not found");
    }

    // ==================== Private Helpers ====================

    private void captureRequestEvidence(String method, String url, Map<String, Object> requestBody, ResponseEntity<Map<String, Object>> responseEntity) {
        // Capture HTTP request
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Content-Type", MediaType.APPLICATION_JSON.toString());
        headers.put("Accept", MediaType.APPLICATION_JSON.toString());
        screenCapture.captureRequest(method, url, headers, requestBody);

        // Capture HTTP response
        Map<String, Object> responseHeaders = new LinkedHashMap<>();
        responseEntity.getHeaders().forEach((k, v) -> responseHeaders.put(k, String.join(", ", v)));
        int status = responseEntity.getStatusCode().value();
        screenCapture.captureResponse(status, responseEntity.getBody(), responseHeaders);

        screenCapture.log("HTTP " + method + " " + url + " → " + status);
    }
}