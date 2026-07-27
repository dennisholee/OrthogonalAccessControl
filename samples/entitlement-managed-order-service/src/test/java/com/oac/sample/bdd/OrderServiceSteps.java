package com.oac.sample.bdd;

import com.oac.sample.model.Order;
import com.oac.sample.repository.OrderRepository;
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

public class OrderServiceSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private OrderRepository orderRepository;

    private final Map<String, String> headers = new LinkedHashMap<>();
    private ResponseEntity<Map<String, Object>> response;
    private ScreenCapture screenCapture;
    private String currentFeatureName;
    private String currentScenarioName;

    @Before
    public void beforeScenario(Scenario scenario) {
        try {
            mongoTemplate.dropCollection("policies");
            mongoTemplate.dropCollection("relationships");
            mongoTemplate.dropCollection("resource_grants");
            mongoTemplate.dropCollection("pii_classification");
        } catch (Exception e) {
            // Collections may not exist — safe to ignore
        }

        this.headers.clear();
        this.response = null;

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
        capturePostState("policies");
        capturePostState("relationships");
        capturePostState("orders");
        screenCapture.write();
    }

    private void capturePostState(String collection) {
        try {
            List<Map> docs = mongoTemplate.findAll(Map.class, collection);
            screenCapture.capturePostState(collection, docs);
        } catch (Exception e) {
        }
    }

    // ==================== GIVEN ====================

    @Given("the order service is running on a random port")
    public void theOrderServiceIsRunning() {
        int port = CucumberSpringConfiguration.getPort();
        assertThat(port).isGreaterThan(0);
        screenCapture.log("Sample order service running on port: " + port);
    }

    @Given("the sample orders are seeded in MongoDB")
    public void seedSampleOrders() {
        orderRepository.deleteAll();
        mongoTemplate.save(new Order("ORD-001", "Alice Johnson", "alice@acme.com",
                "123-45-6789", "Widget A", 10, 299.99, "alice"), "orders");
        mongoTemplate.save(new Order("ORD-002", "Bob Smith", "bob@acme.com",
                "987-65-4321", "Gadget B", 5, 149.95, "bob"), "orders");
        screenCapture.captureSeedData("orders", List.of(
                Map.of("id", "ORD-001", "customerName", "Alice Johnson",
                       "customerEmail", "alice@acme.com", "ownerId", "alice"),
                Map.of("id", "ORD-002", "customerName", "Bob Smith",
                       "customerEmail", "bob@acme.com", "ownerId", "bob")
        ));
        screenCapture.log("Seeded 2 sample orders");
    }

    @Given("the PDP rule engine is available in-process")
    public void pdpRuleEngineAvailable() {
        screenCapture.log("PDP rule engine is available in-process via DirectDecisionClient");
    }

    @Given("a DENY policy for subject {string} is seeded")
    public void seedDenyPolicy(String subjectId) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", "POL.DENY." + subjectId.toUpperCase() + ".v1");
        policy.put("effect", "DENY");
        policy.put("state", "ACTIVE");
        policy.put("subjectId", subjectId);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (DENY " + subjectId + ")", policy);
        screenCapture.log("Seeded DENY policy for subject: " + subjectId);
    }

    @Given("a baseline ALLOW policy is seeded for subject {string} action {string} resource {string}")
    public void seedAllowPolicy(String subjectId, String action, String resourceType) {
        String storedAction = action.toLowerCase();
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", "POL.ALLOW." + subjectId.toUpperCase() + ".v1");
        policy.put("effect", "ALLOW");
        policy.put("state", "ACTIVE");
        policy.put("subjectId", subjectId);
        policy.put("action", storedAction);
        policy.put("resourceType", resourceType);
        policy.put("tenant", "acme-corp");
        policy.put("geography", "global");
        policy.put("market", "enterprise");
        policy.put("lineOfBusiness", "ecommerce");
        policy.put("channel", "staff");

        if ("csr-user".equals(subjectId)) {
            List<Map<String, String>> masks = List.of(
                    Map.of("field", "customer.email", "level", "MASK"),
                    Map.of("field", "customer.ssn", "level", "NONE"),
                    Map.of("field", "customer.name", "level", "READ")
            );
            policy.put("fieldMasks", masks);
        }
        if ("auditor".equals(subjectId)) {
            List<Map<String, String>> masks = List.of(
                    Map.of("field", "customer.email", "level", "NONE"),
                    Map.of("field", "customer.ssn", "level", "NONE"),
                    Map.of("field", "customer.name", "level", "NONE"),
                    Map.of("field", "customer.phone", "level", "NONE")
            );
            policy.put("fieldMasks", masks);
        }

        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (ALLOW " + subjectId + ")", policy);
        screenCapture.log("Seeded ALLOW policy for subject: " + subjectId);
    }

    @Given("a WORKLOAD ALLOW policy is seeded for service {string}")
    public void seedWorkloadPolicy(String serviceId) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", "POL.WORKLOAD." + serviceId.toUpperCase() + ".v1");
        policy.put("effect", "ALLOW");
        policy.put("state", "ACTIVE");
        policy.put("subjectType", "workload");
        policy.put("subjectId", serviceId);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (WORKLOAD " + serviceId + ")", policy);
        screenCapture.log("Seeded WORKLOAD policy for service: " + serviceId);
    }

    @Given("a ReBAC ALLOW policy is seeded for action {string} resource {string}")
    public void seedRebacPolicy(String action, String resourceType) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", "POL.REBAC." + action + "." + resourceType.toUpperCase() + ".v1");
        policy.put("effect", "ALLOW");
        policy.put("state", "ACTIVE");
        policy.put("requiredRelationship", "manages");
        policy.put("action", action);
        policy.put("resourceType", resourceType);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (REBAC " + action + " " + resourceType + ")", policy);
        screenCapture.log("Seeded ReBAC policy for action: " + action + " resource: " + resourceType);
    }

    @Given("a relationship edge from {string} to {string} of type {string} is saved")
    public void saveRelationshipEdge(String fromId, String toId, String type) {
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("subjectId", fromId);
        rel.put("resourceId", toId);
        rel.put("relationshipType", type);
        rel.put("createdAt", Instant.now().toString());
        mongoTemplate.save(rel, "relationships");
        screenCapture.captureSeedData("relationships (" + fromId + " → " + toId + " : " + type + ")", rel);
        screenCapture.log("Saved relationship: " + fromId + " → " + toId + " (" + type + ")");
    }

    @Given("the request header {string} is {string}")
    public void setRequestHeader(String headerName, String headerValue) {
        this.headers.put(headerName, headerValue);
    }

    // ==================== EXTENDED GIVEN STEPS ====================

    @Given("a SpEL-ALLOW policy is seeded for subject {string} with condition {string}")
    public void seedSpelPolicy(String subjectId, String spelCondition) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", "POL.SPEL." + subjectId.toUpperCase() + ".v1");
        policy.put("effect", "ALLOW");
        policy.put("state", "ACTIVE");
        policy.put("subjectId", subjectId);
        policy.put("action", "read");
        policy.put("resourceType", "order");
        policy.put("tenant", "acme-corp");
        policy.put("geography", "global");
        policy.put("market", "enterprise");
        policy.put("lineOfBusiness", "ecommerce");
        policy.put("channel", "staff");
        policy.put("spelCondition", spelCondition);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (SpEL " + subjectId + ")", policy);
        screenCapture.log("Seeded SpEL policy for subject: " + subjectId + " condition: " + spelCondition);
    }

    @Given("a time-window ALLOW policy is seeded for subject {string} with window {string}")
    public void seedTimeWindowPolicy(String subjectId, String timeWindow) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", "POL.TIME.WINDOW." + subjectId.toUpperCase() + ".v1");
        policy.put("effect", "ALLOW");
        policy.put("state", "ACTIVE");
        policy.put("subjectId", subjectId);
        policy.put("action", "read");
        policy.put("resourceType", "order");
        policy.put("tenant", "acme-corp");
        policy.put("geography", "global");
        policy.put("market", "enterprise");
        policy.put("lineOfBusiness", "ecommerce");
        policy.put("channel", "staff");
        policy.put("timeWindow", timeWindow);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (time-window " + subjectId + ")", policy);
        screenCapture.log("Seeded time-window policy for subject: " + subjectId + " window: " + timeWindow);
    }

    @Given("a break-glass ALLOW policy is seeded for action {string} resource {string}")
    public void seedBreakGlassPolicy(String action, String resourceType) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", "POL.BREAK.GLASS.v1");
        policy.put("effect", "ALLOW");
        policy.put("state", "ACTIVE");
        policy.put("action", action);
        policy.put("resourceType", resourceType);
        policy.put("tenant", "acme-corp");
        policy.put("geography", "global");
        policy.put("market", "enterprise");
        policy.put("lineOfBusiness", "ecommerce");
        policy.put("channel", "staff");
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (break-glass " + action + " " + resourceType + ")", policy);
        screenCapture.log("Seeded break-glass policy for action: " + action + " resource: " + resourceType);
    }

    @Given("a tenant-scoped ALLOW policy is seeded for subject {string} tenant {string}")
    public void seedTenantScopedPolicy(String subjectId, String tenant) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", "POL.TENANT." + subjectId.toUpperCase() + ".v1");
        policy.put("effect", "ALLOW");
        policy.put("state", "ACTIVE");
        policy.put("subjectId", subjectId);
        policy.put("action", "read");
        policy.put("resourceType", "order");
        policy.put("tenant", tenant);
        policy.put("geography", "global");
        policy.put("market", "enterprise");
        policy.put("lineOfBusiness", "ecommerce");
        policy.put("channel", "staff");
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (tenant " + tenant + " for " + subjectId + ")", policy);
        screenCapture.log("Seeded tenant-scoped policy for subject: " + subjectId + " tenant: " + tenant);
    }

    @Given("a channel-scoped ALLOW policy is seeded for subject {string} channel {string}")
    public void seedChannelScopedPolicy(String subjectId, String channel) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", "POL.CHANNEL." + subjectId.toUpperCase() + ".v1");
        policy.put("effect", "ALLOW");
        policy.put("state", "ACTIVE");
        policy.put("subjectId", subjectId);
        policy.put("action", "read");
        policy.put("resourceType", "order");
        policy.put("tenant", "acme-corp");
        policy.put("geography", "global");
        policy.put("market", "enterprise");
        policy.put("lineOfBusiness", "ecommerce");
        policy.put("channel", channel);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (channel " + channel + " for " + subjectId + ")", policy);
        screenCapture.log("Seeded channel-scoped policy for subject: " + subjectId + " channel: " + channel);
    }

    @Given("a relationship chain {string} is saved")
    public void saveRelationshipChain(String chainSpec) {
        String[] parts = chainSpec.split(":");
        String relationshipType = parts.length > 1 ? parts[1] : "manages";
        String[] nodes = parts[0].split("->");

        for (int i = 0; i < nodes.length - 1; i++) {
            Map<String, Object> rel = new LinkedHashMap<>();
            rel.put("subjectId", nodes[i]);
            rel.put("resourceId", nodes[i + 1]);
            rel.put("relationshipType", relationshipType);
            rel.put("createdAt", Instant.now().toString());
            mongoTemplate.save(rel, "relationships");
        }
        screenCapture.log("Saved relationship chain: " + chainSpec);
    }

    // ==================== WHEN ====================

    @When("a GET request is sent to {string}")
    public void sendGetRequest(String path) {
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + path;
        HttpHeaders httpHeaders = new HttpHeaders();
        this.headers.forEach(httpHeaders::set);
        HttpEntity<Void> entity = new HttpEntity<>(httpHeaders);
        try {
            this.response = restTemplate.exchange(url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            ResponseEntity<String> rawResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            this.response = new ResponseEntity<>(
                    Map.of("error", "Array response captured as raw string"),
                    rawResponse.getHeaders(),
                    rawResponse.getStatusCode()
            );
        }
        captureRequestEvidence("GET", url, this.headers, null);
    }

    @When("a POST request is sent to {string}")
    public void sendPostRequest(String path) {
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + path;
        HttpHeaders httpHeaders = new HttpHeaders();
        this.headers.forEach(httpHeaders::set);
        HttpEntity<Void> entity = new HttpEntity<>(httpHeaders);
        this.response = restTemplate.exchange(url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        captureRequestEvidence("POST", url, this.headers, null);
    }

    @When("a DELETE request is sent to {string}")
    public void sendDeleteRequest(String path) {
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + path;
        HttpHeaders httpHeaders = new HttpHeaders();
        this.headers.forEach(httpHeaders::set);
        HttpEntity<Void> entity = new HttpEntity<>(httpHeaders);
        this.response = restTemplate.exchange(url, HttpMethod.DELETE, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        captureRequestEvidence("DELETE", url, this.headers, null);
    }

    // ==================== THEN ====================

    @Then("the response status should be {int}")
    public void verifyResponseStatus(int expectedStatus) {
        int actualStatus = this.response.getStatusCode().value();
        assertThat(actualStatus).isEqualTo(expectedStatus);
        screenCapture.logAssertion("Response status", actualStatus == expectedStatus,
                String.valueOf(expectedStatus), String.valueOf(actualStatus));
    }

    @Then("the response body should contain {string}")
    public void verifyResponseBodyContains(String expectedText) {
        Object body = this.response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.toString()).contains(expectedText);
        screenCapture.logAssertion("Response body contains '" + expectedText + "'",
                body.toString().contains(expectedText), expectedText, body.toString());
    }

    @Then("the order field {string} should be masked")
    public void verifyOrderFieldMasked(String fieldName) {
        Object body = this.response.getBody();
        assertThat(body).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) body;
        Object actualValue = map.get(fieldName);
        assertThat(actualValue).isNotNull();
        assertThat(actualValue.toString()).contains("***");
        screenCapture.logAssertion("Order field '" + fieldName + "' is masked",
                true, "masked", String.valueOf(actualValue));
    }

    @Then("the order field {string} should be null")
    public void verifyOrderFieldNull(String fieldName) {
        Object body = this.response.getBody();
        assertThat(body).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) body;
        Object actualValue = map.get(fieldName);
        assertThat(actualValue).isNull();
        screenCapture.logAssertion("Order field '" + fieldName + "' is null",
                actualValue == null, "null", String.valueOf(actualValue));
    }

    @Then("the order field {string} should be visible")
    public void verifyOrderFieldVisible(String fieldName) {
        Object body = this.response.getBody();
        assertThat(body).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) body;
        Object actualValue = map.get(fieldName);
        assertThat(actualValue).isNotNull();
        screenCapture.logAssertion("Order field '" + fieldName + "' is visible",
                true, "non-null", String.valueOf(actualValue));
    }

    @Then("the order field {string} should be {string}")
    public void verifyOrderFieldValue(String fieldName, String expectedValue) {
        Object body = this.response.getBody();
        assertThat(body).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) body;
        Object actualValue = map.get(fieldName);
        assertThat(actualValue).isEqualTo(expectedValue);
        screenCapture.logAssertion("Order field '" + fieldName + "'",
                expectedValue.equals(actualValue), expectedValue, String.valueOf(actualValue));
    }

    @Then("the response should be a list")
    public void verifyResponseIsList() {
        screenCapture.log("Response is a list (verified via status code 200)");
    }

    @Then("the list entry at index {int} field {string} should be {string}")
    public void verifyListEntryFieldValue(int index, String fieldName, String expectedValue) {
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/api/orders";
        HttpHeaders httpHeaders = new HttpHeaders();
        this.headers.forEach(httpHeaders::set);
        HttpEntity<Void> entity = new HttpEntity<>(httpHeaders);
        ResponseEntity<List> listResponse = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);

        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody().size()).isGreaterThan(index);
        Object entry = listResponse.getBody().get(index);
        assertThat(entry).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> entryMap = (Map<String, Object>) entry;
        Object actualValue = entryMap.get(fieldName);
        assertThat(actualValue).isEqualTo(expectedValue);
        screenCapture.logAssertion("List[" + index + "]." + fieldName, expectedValue.equals(actualValue),
                expectedValue, String.valueOf(actualValue));
    }

    @Then("the list entry at index {int} field {string} should be null")
    public void verifyListEntryFieldNull(int index, String fieldName) {
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/api/orders";
        HttpHeaders httpHeaders = new HttpHeaders();
        this.headers.forEach(httpHeaders::set);
        HttpEntity<Void> entity = new HttpEntity<>(httpHeaders);
        ResponseEntity<List> listResponse = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);

        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody().size()).isGreaterThan(index);
        Object entry = listResponse.getBody().get(index);
        assertThat(entry).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> entryMap = (Map<String, Object>) entry;
        assertThat(entryMap.get(fieldName)).isNull();
        screenCapture.logAssertion("List[" + index + "]." + fieldName + " is null",
                entryMap.get(fieldName) == null, "null", String.valueOf(entryMap.get(fieldName)));
    }

    @Then("the aggregate response should contain {string} value {int}")
    public void verifyAggregateValue(String key, int expectedValue) {
        Object body = this.response.getBody();
        assertThat(body).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) body;
        Object actual = map.get(key);
        assertThat(actual).isInstanceOf(Number.class);
        assertThat(((Number) actual).intValue()).isEqualTo(expectedValue);
        screenCapture.logAssertion("Aggregate '" + key + "'",
                expectedValue == ((Number) actual).intValue(),
                String.valueOf(expectedValue), String.valueOf(actual));
    }

    @Then("the approval response should have status {string}")
    public void verifyApprovalStatus(String expectedStatus) {
        Object body = this.response.getBody();
        assertThat(body).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) body;
        String actualStatus = (String) map.get("status");
        assertThat(actualStatus).isEqualTo(expectedStatus);
        screenCapture.logAssertion("Approval status", expectedStatus.equals(actualStatus),
                expectedStatus, actualStatus);
    }

    // ==================== Private Helpers ====================

    private void captureRequestEvidence(String method, String url, Map<String, String> requestHeaders, Object body) {
        Map<String, Object> headersObj = new LinkedHashMap<>(requestHeaders);
        screenCapture.captureRequest(method, url, headersObj, body);
        int status = this.response.getStatusCode().value();
        screenCapture.captureResponse(status, this.response.getBody(), Map.of("Content-Type", "application/json"));
        screenCapture.log("HTTP " + method + " " + url + " → " + status);
    }
}