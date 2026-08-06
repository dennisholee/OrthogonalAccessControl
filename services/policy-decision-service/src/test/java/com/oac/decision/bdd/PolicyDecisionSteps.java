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
            "policies", "relationships", "resource_grants", "pii_classification",
            "consistency_tokens", "controller_purpose_registry", "shadow_decisions",
            "policy_sets", "attribute_schema");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private com.oac.decision.adapter.out.health.OacDecisionHealthIndicator oacHealthIndicator;

    @Autowired
    private com.oac.decision.application.service.decision.CircuitBreaker circuitBreaker;

    @Autowired
    private com.oac.decision.application.service.decision.DecisionCache decisionCache;

    @Autowired
    private com.oac.decision.application.port.out.ConsistencyTokenStore consistencyTokenStore;

    @Autowired
    private com.oac.decision.adapter.out.audit.InMemoryAuditEvidenceAdapter auditEvidenceAdapter;

    private final Map<String, Object> requestBody = new LinkedHashMap<>();
    private final Map<String, Object> runtimeContext = new LinkedHashMap<>();
    private final Map<String, Object> boundaryContext = new LinkedHashMap<>();
    private final Map<String, String> issuedTokensByScope = new LinkedHashMap<>();
    private ResponseEntity<Map<String, Object>> response;
    private ResponseEntity<Map<String, Object>> policyCreateResponse;
    private ResponseEntity<List<Map<String, Object>>> auditEventsResponse;
    private String capturedConsistencyToken;
    private ScreenCapture screenCapture;
    private String currentFeatureName;
    private String currentScenarioName;
    private String lastSavedPolicyName;

    @Before
    public void beforeScenario(Scenario scenario) {
        // Drop all collections between scenarios to prevent cross-contamination
        try {
            mongoTemplate.dropCollection("policies");
            mongoTemplate.dropCollection("relationships");
            mongoTemplate.dropCollection("resource_grants");
            mongoTemplate.dropCollection("pii_classification");
            mongoTemplate.dropCollection("consistency_tokens");
            mongoTemplate.dropCollection("controller_purpose_registry");
            mongoTemplate.dropCollection("shadow_decisions");
            mongoTemplate.dropCollection("policy_sets");
            mongoTemplate.dropCollection("attribute_schema");
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

        // Reset resilient in-process state between scenarios to avoid cross-contamination.
        if (this.circuitBreaker != null) this.circuitBreaker.reset();
        if (this.decisionCache != null) this.decisionCache.reset();
        if (this.auditEvidenceAdapter != null) this.auditEvidenceAdapter.clear();
        if (this.oacHealthIndicator != null) this.oacHealthIndicator.setDegraded(false);

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
        this.lastSavedPolicyName = name;
        screenCapture.captureSeedData("policies (" + name + ")", policy);
        screenCapture.log("Saved policy: " + name + " effect=" + effect);
    }

    @Given("a policy document with effect {string} and name {string} for action {string} and resource type {string} is saved to MongoDB")
    public void saveScopedPolicyToMongoDB(String effect, String name, String action, String resourceType) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        policy.put("action", action);
        policy.put("resourceType", resourceType);
        mongoTemplate.save(policy, "policies");
        this.lastSavedPolicyName = name;
        screenCapture.captureSeedData("policies (" + name + " scoped to " + action + "/" + resourceType + ")", policy);
        screenCapture.log("Saved scoped policy: " + name + " effect=" + effect
                + " action=" + action + " resourceType=" + resourceType);
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
        this.lastSavedPolicyName = name;
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

    @Given("a suppression flag {string} with value {string}")
    public void setSuppressionFlag(String flagName, String flagValue) {
        @SuppressWarnings("unchecked")
        Map<String, Boolean> suppressionFlags = (Map<String, Boolean>) this.requestBody.computeIfAbsent(
                "suppressionFlags", k -> new LinkedHashMap<String, Boolean>());
        suppressionFlags.put(flagName, Boolean.valueOf(flagValue));
        screenCapture.log("Set suppression flag: " + flagName + " = " + flagValue);
    }

    @Given("a consent attribute {string} with status {string}")
    public void setConsentAttribute(String attributeName, String status) {
        @SuppressWarnings("unchecked")
        Map<String, Object> consentAttributes = (Map<String, Object>) this.requestBody.computeIfAbsent(
                "consentAttributes", k -> new LinkedHashMap<String, Object>());
        consentAttributes.put(attributeName, status.toUpperCase());
        screenCapture.log("Set consent attribute: " + attributeName + " = " + status.toUpperCase());
    }

    @Given("a consent version {string}")
    public void setConsentVersion(String version) {
        this.requestBody.put("consentVersion", version);
        screenCapture.log("Set consent version: " + version);
    }

    @Given("a cross-boundary justification {string}")
    public void setCrossBoundaryJustification(String justification) {
        this.requestBody.put("crossBoundaryJustification", justification);
        screenCapture.log("Set cross-boundary justification: " + justification);
    }

    @Given("a boundary context tenant {string} geography {string} market {string} lineOfBusiness {string} channel {string} purpose {string}")
    public void setBoundaryContextWithPurposeOnly(String tenant, String geography, String market, String lob, String channel, String purpose) {
        this.boundaryContext.clear();
        this.boundaryContext.put("tenant", tenant);
        this.boundaryContext.put("geography", geography);
        this.boundaryContext.put("market", market);
        this.boundaryContext.put("lineOfBusiness", lob);
        this.boundaryContext.put("channel", channel);
        if (purpose != null && !purpose.isBlank() && !"*".equals(purpose)) {
            this.boundaryContext.put("purpose", purpose);
        }
        this.requestBody.put("boundaryContext", this.boundaryContext);
        screenCapture.log("Set boundary context (purpose only): tenant=" + tenant + " geo=" + geography
                + " market=" + market + " lob=" + lob + " channel=" + channel + " purpose=" + purpose);
    }

    @Given("a boundary context tenant {string} geography {string} market {string} lineOfBusiness {string} channel {string} purpose {string} regulatoryRegime {string}")
    public void setBoundaryContextWithPurpose(String tenant, String geography, String market, String lob, String channel, String purpose, String regime) {
        this.boundaryContext.clear();
        this.boundaryContext.put("tenant", tenant);
        this.boundaryContext.put("geography", geography);
        this.boundaryContext.put("market", market);
        this.boundaryContext.put("lineOfBusiness", lob);
        this.boundaryContext.put("channel", channel);
        if (purpose != null && !purpose.isBlank() && !"*".equals(purpose)) {
            this.boundaryContext.put("purpose", purpose);
        }
        if (regime != null && !regime.isBlank() && !"*".equals(regime)) {
            this.boundaryContext.put("regulatoryRegime", regime);
        }
        this.requestBody.put("boundaryContext", this.boundaryContext);
        screenCapture.log("Set boundary context: tenant=" + tenant + " geo=" + geography + " market=" + market
                + " lob=" + lob + " channel=" + channel + " purpose=" + purpose + " regime=" + regime);
    }

    @Given("a CDP policy document with purpose {string} is saved to MongoDB")
    public void saveCdpPolicyForPurpose(String purpose) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.CDP.PURPOSE." + purpose.toUpperCase().replaceAll("[^a-zA-Z0-9_-]", "_");
        policy.put("name", name);
        policy.put("effect", "ALLOW");
        policy.put("state", "ACTIVE");
        policy.put("action", "*");
        policy.put("resourceType", "*");
        policy.put("purpose", purpose);
        mongoTemplate.save(policy, "policies");
        this.lastSavedPolicyName = name;
        screenCapture.captureSeedData("policies (CDP purpose: " + name + ")", policy);
        screenCapture.log("Saved CDP policy for purpose: " + purpose);
    }

    @Given("a CDP policy document with regulatory regime {string} is saved to MongoDB")
    public void saveCdpPolicyForRegulatoryRegime(String regime) {
        Map<String, Object> policy = new LinkedHashMap<>();
        String name = "POL.CDP.REGIME." + regime.toUpperCase().replaceAll("[^a-zA-Z0-9_-]", "_");
        policy.put("name", name);
        policy.put("effect", "ALLOW");
        policy.put("state", "ACTIVE");
        policy.put("action", "*");
        policy.put("resourceType", "*");
        policy.put("regulatoryRegime", regime);
        mongoTemplate.save(policy, "policies");
        this.lastSavedPolicyName = name;
        screenCapture.captureSeedData("policies (CDP regime: " + name + ")", policy);
        screenCapture.log("Saved CDP policy for regulatory regime: " + regime);
    }

    @Given("the policy has purpose {string}")
    public void updatePolicyPurpose(String purpose) {
        updateLastSavedPolicy(policy -> policy.put("purpose", purpose));
        screenCapture.log("Set purpose on policy " + this.lastSavedPolicyName + ": " + purpose);
    }

    @Given("the policy has purpose array [{string}, {string}]")
    public void updatePolicyPurposeArray(String v1, String v2) {
        List<Object> values = List.of(v1, v2);
        updateLastSavedPolicy(policy -> policy.put("purpose", values));
        screenCapture.log("Set purpose array on policy " + this.lastSavedPolicyName + ": " + values);
    }

    @Given("the policy has purpose array [{string}, {string}, {string}]")
    public void updatePolicyPurposeArray3(String v1, String v2, String v3) {
        List<Object> values = List.of(v1, v2, v3);
        updateLastSavedPolicy(policy -> policy.put("purpose", values));
        screenCapture.log("Set purpose array on policy " + this.lastSavedPolicyName + ": " + values);
    }

    @Given("the policy has regulatoryRegime {string}")
    public void updatePolicyRegulatoryRegime(String regime) {
        updateLastSavedPolicy(policy -> policy.put("regulatoryRegime", regime));
        screenCapture.log("Set regulatoryRegime on policy " + this.lastSavedPolicyName + ": " + regime);
    }

    @Given("the policy has regulatoryRegime array [{string}, {string}]")
    public void updatePolicyRegulatoryRegimeArray(String v1, String v2) {
        List<Object> values = List.of(v1, v2);
        updateLastSavedPolicy(policy -> policy.put("regulatoryRegime", values));
        screenCapture.log("Set regulatoryRegime array on policy " + this.lastSavedPolicyName + ": " + values);
    }

    @Given("the policy has regulatoryRegime array [{string}, {string}, {string}]")
    public void updatePolicyRegulatoryRegimeArray3(String v1, String v2, String v3) {
        List<Object> values = List.of(v1, v2, v3);
        updateLastSavedPolicy(policy -> policy.put("regulatoryRegime", values));
        screenCapture.log("Set regulatoryRegime array on policy " + this.lastSavedPolicyName + ": " + values);
    }

    @Given("the policy has spelCondition {string}")
    public void updatePolicySpelCondition(String spelCondition) {
        updateLastSavedPolicy(policy -> policy.put("spelCondition", spelCondition));
        screenCapture.log("Set spelCondition on policy " + this.lastSavedPolicyName + ": " + spelCondition);
    }

    @Given("the policy has requiredRelationship {string}")
    public void updatePolicyRequiredRelationship(String relationshipType) {
        updateLastSavedPolicy(policy -> policy.put("requiredRelationship", relationshipType));
        screenCapture.log("Set requiredRelationship on policy " + this.lastSavedPolicyName + ": " + relationshipType);
    }

    @Given("the policy has resourceTypes {string}")
    public void updatePolicyResourceTypes(String resourceTypesCsv) {
        List<Object> values = new ArrayList<>(splitCsv(resourceTypesCsv));
        updateLastSavedPolicy(policy -> policy.put("resourceTypes", values));
        screenCapture.log("Set resourceTypes array on policy " + this.lastSavedPolicyName
                + ": " + resourceTypesCsv);
    }

    @Given("the policy has environment {string}")
    public void updatePolicyEnvironment(String environment) {
        updateLastSavedPolicy(policy -> policy.put("environment", environment));
        screenCapture.log("Set environment on policy " + this.lastSavedPolicyName + ": " + environment);
    }

    @Given("a relationship edge from {string} to {string} of type {string} with boundaryScope market {string} and lineOfBusiness {string} is saved to MongoDB")
    public void saveRelationshipEdgeWithBoundaryScope(String fromId, String toId, String type,
                                                      String market, String lob) {
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("subjectId", fromId);
        rel.put("resourceId", toId);
        rel.put("relationshipType", type);
        rel.put("createdAt", Instant.now().toString());
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("market", market);
        scope.put("lineOfBusiness", lob);
        rel.put("boundaryScope", scope);
        mongoTemplate.save(rel, "relationships");
        screenCapture.captureSeedData("relationships ("
                + fromId + " → " + toId + " : " + type + " scope={" + market + "/" + lob + "})", rel);
        screenCapture.log("Saved scoped relationship edge: " + fromId + " → " + toId
                + " type=" + type + " scope={market:" + market + ", lob:" + lob + "}");
    }

    @Given("the policy requires relationship {string} with boundaryScope market {string} and lineOfBusiness {string}")
    public void updatePolicyRelationshipBoundaryScope(String relationshipType, String market, String lob) {
        updateLastSavedPolicy(policy -> {
            policy.put("requiredRelationship", relationshipType);
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("market", market);
            scope.put("lineOfBusiness", lob);
            policy.put("relationshipBoundaryScope", scope);
        });
        screenCapture.log("Set relationshipBoundaryScope on policy " + this.lastSavedPolicyName
                + ": type=" + relationshipType + " scope={market:" + market + ", lob:" + lob + "}");
    }

    @Given("a relationship edge from {string} to {string} of type {string} with boundaryScope market {string} is saved to MongoDB")
    public void saveRelationshipEdgeWithBoundaryScopeMarketOnly(String fromId, String toId, String type, String market) {
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("subjectId", fromId);
        rel.put("resourceId", toId);
        rel.put("relationshipType", type);
        rel.put("createdAt", Instant.now().toString());
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("market", market);
        rel.put("boundaryScope", scope);
        mongoTemplate.save(rel, "relationships");
        screenCapture.captureSeedData("relationships ("
                + fromId + " → " + toId + " : " + type + " scope={market:" + market + "})", rel);
        screenCapture.log("Saved scoped relationship edge: " + fromId + " → " + toId
                + " type=" + type + " scope={market:" + market + "}");
    }

    @Given("the policy requires relationship {string}")
    public void updatePolicyRequiredRelationshipOnly(String relationshipType) {
        updateLastSavedPolicy(policy -> policy.put("requiredRelationship", relationshipType));
        screenCapture.log("Set requiredRelationship on policy " + this.lastSavedPolicyName + ": " + relationshipType);
    }
    @Given("a policy set with id {string} combining {string} and policies {string} is saved to MongoDB")
    public void savePolicySet(String setId, String algorithm, String policyIdsCsv) {
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("setId", setId);
        set.put("name", "Policy Set " + setId);
        set.put("combiningAlgorithm", algorithm);
        set.put("policyIds", splitCsv(policyIdsCsv));
        set.put("version", 1);
        mongoTemplate.save(set, "policy_sets");
        screenCapture.captureSeedData("policy_sets (" + setId + ")", set);
        screenCapture.log("Saved policy set " + setId + " combining " + algorithm
                + " policies=" + policyIdsCsv);
    }

    @Given("the policy set {string} has environment {string}")
    public void setPolicySetEnvironment(String setId, String environment) {
        updatePolicySet(setId, set -> set.put("environment", environment));
        screenCapture.log("Set policy set " + setId + " environment=" + environment);
    }

    @Given("the policy set {string} has canary by-tenant {string}")
    public void setPolicySetCanary(String setId, String tenantsCsv) {
        updatePolicySet(setId, set -> set.put("canary", Map.of(
                "enabled", true,
                "target", "by-tenant",
                "targetValues", splitCsv(tenantsCsv))));
        screenCapture.log("Set policy set " + setId + " canary by-tenant " + tenantsCsv);
    }

    @Given("the attribute schema entry {string} of type {string} is registered and required")
    public void registerRequiredAttributeSchema(String attributeName, String attributeType) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("attributeName", attributeName);
        schema.put("attributeType", attributeType);
        schema.put("cardinality", "SINGLE");
        schema.put("source", "resource-metadata");
        schema.put("sensitivity", "RESTRICTED");
        schema.put("isRequired", true);
        mongoTemplate.save(schema, "attribute_schema");
        screenCapture.captureSeedData("attribute_schema (" + attributeName + ")", schema);
        screenCapture.log("Registered required attribute schema: " + attributeName
                + " type=" + attributeType);
    }

    private void updatePolicySet(String setId, java.util.function.Consumer<Map<String, Object>> updater) {
        Map<String, Object> set = mongoTemplate.findOne(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("setId").is(setId)),
                Map.class, "policy_sets");
        if (set != null) {
            updater.accept(set);
            mongoTemplate.save(set, "policy_sets");
        }
    }

    @Given("principal domain membership tenants {string} markets {string} geographies {string} linesOfBusiness {string} channels {string}")
    public void setPrincipalMemberships(String tenants, String markets, String geographies, String lobs, String channels) {
        setPrincipalMembershipsAll(tenants, markets, geographies, lobs, channels, null, null);
    }

    @Given("principal domain membership tenants {string} markets {string} geographies {string} linesOfBusiness {string} channels {string} purposes {string}")
    public void setPrincipalMembershipsWithPurposes(String tenants, String markets, String geographies, String lobs, String channels, String purposes) {
        setPrincipalMembershipsAll(tenants, markets, geographies, lobs, channels, purposes, null);
    }

    @Given("principal domain membership tenants {string} markets {string} geographies {string} linesOfBusiness {string} channels {string} regulatoryRegimes {string}")
    public void setPrincipalMembershipsWithRegimes(String tenants, String markets, String geographies, String lobs, String channels, String regimes) {
        setPrincipalMembershipsAll(tenants, markets, geographies, lobs, channels, null, regimes);
    }

    private void setPrincipalMembershipsAll(String tenants, String markets, String geographies, String lobs,
                                            String channels, String purposes, String regimes) {
        Map<String, Object> pm = new LinkedHashMap<>();
        pm.put("tenants", splitCsv(tenants));
        pm.put("markets", splitCsv(markets));
        pm.put("geographies", splitCsv(geographies));
        pm.put("linesOfBusiness", splitCsv(lobs));
        pm.put("channels", splitCsv(channels));
        if (purposes != null && !purposes.isBlank()) pm.put("purposes", splitCsv(purposes));
        if (regimes != null && !regimes.isBlank()) pm.put("regulatoryRegimes", splitCsv(regimes));
        this.requestBody.put("principalMemberships", pm);
        screenCapture.log("Set principal domain memberships: " + pm);
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList());
    }

    @Given("the policy has effectiveFrom {string}")
    public void updatePolicyEffectiveFrom(String effectiveFrom) {
        updateLastSavedPolicy(policy -> policy.put("effectiveFrom", effectiveFrom));
        screenCapture.log("Set effectiveFrom on policy " + this.lastSavedPolicyName + ": " + effectiveFrom);
    }

    @Given("the policy has effectiveUntil {string}")
    public void updatePolicyEffectiveUntil(String effectiveUntil) {
        updateLastSavedPolicy(policy -> policy.put("effectiveUntil", effectiveUntil));
        screenCapture.log("Set effectiveUntil on policy " + this.lastSavedPolicyName + ": " + effectiveUntil);
    }

    @Given("the policy has policyType {string}")
    public void updatePolicyType(String policyType) {
        updateLastSavedPolicy(policy -> policy.put("policyType", policyType));
        screenCapture.log("Set policyType on policy " + this.lastSavedPolicyName + ": " + policyType);
    }

    @Given("a controller purpose {string} is registered for tenant {string} with lawful basis {string}")
    public void registerControllerPurpose(String purpose, String tenant, String lawfulBasis) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("tenant", tenant);
        doc.put("purpose", purpose);
        doc.put("lawfulBasis", lawfulBasis);
        mongoTemplate.save(doc, "controller_purpose_registry");
        screenCapture.captureSeedData("controller_purpose_registry ("
                + tenant + "::" + purpose + " basis=" + lawfulBasis + ")", doc);
        screenCapture.log("Registered controller purpose: tenant=" + tenant
                + " purpose=" + purpose + " lawfulBasis=" + lawfulBasis);
    }

    @Given("the policy has effectiveWindow from now minus {int} hours to now plus {int} hours")
    public void updatePolicyEffectiveWindow(int minusHours, int plusHours) {
        String from = java.time.Instant.now().minus(minusHours, java.time.temporal.ChronoUnit.HOURS).toString();
        String until = java.time.Instant.now().plus(plusHours, java.time.temporal.ChronoUnit.HOURS).toString();
        updateLastSavedPolicy(policy -> {
            policy.put("effectiveFrom", from);
            policy.put("effectiveUntil", until);
        });
        screenCapture.log("Set effectiveWindow on policy " + this.lastSavedPolicyName
                + ": from=" + from + " until=" + until);
    }

    @Given("the policy has tenant {string}")
    public void updatePolicyTenant(String tenant) {
        updateLastSavedPolicy(policy -> policy.put("tenant", tenant));
        screenCapture.log("Set tenant on policy " + this.lastSavedPolicyName + ": " + tenant);
    }

    @Given("the policy inheritsFrom {string}")
    public void updatePolicyInheritsFrom(String parentName) {
        updateLastSavedPolicy(policy -> policy.put("inheritsFrom", parentName));
        screenCapture.log("Set inheritsFrom on policy " + this.lastSavedPolicyName + ": " + parentName);
    }

    @Given("the policy overrides tenant {string}")
    public void updatePolicyOverridesTenant(String tenant) {
        updateLastSavedPolicy(policy -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> overrides = (Map<String, Object>) policy.computeIfAbsent(
                    "overrides", k -> new LinkedHashMap<String, Object>());
            overrides.put("tenant", tenant);
        });
        screenCapture.log("Set overrides.tenant on policy " + this.lastSavedPolicyName + ": " + tenant);
    }

    @Given("the policy overrides geography {string}")
    public void updatePolicyOverridesGeography(String geography) {
        updateLastSavedPolicy(policy -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> overrides = (Map<String, Object>) policy.computeIfAbsent(
                    "overrides", k -> new LinkedHashMap<String, Object>());
            overrides.put("geography", geography);
        });
        screenCapture.log("Set overrides.geography on policy " + this.lastSavedPolicyName + ": " + geography);
    }

    @Given("the policy overrides market {string}")
    public void updatePolicyOverridesMarket(String market) {
        updateLastSavedPolicy(policy -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> overrides = (Map<String, Object>) policy.computeIfAbsent(
                    "overrides", k -> new LinkedHashMap<String, Object>());
            overrides.put("market", market);
        });
        screenCapture.log("Set overrides.market on policy " + this.lastSavedPolicyName + ": " + market);
    }

    @Given("the policy has tenant {string} geography {string} market {string} lineOfBusiness {string} channel {string}")
    public void updatePolicyBoundaryFields(String tenant, String geography, String market, String lob, String channel) {
        updateLastSavedPolicy(policy -> {
            policy.put("tenant", tenant);
            policy.put("geography", geography);
            policy.put("market", market);
            policy.put("lineOfBusiness", lob);
            policy.put("channel", channel);
        });
        screenCapture.log("Set boundary fields on policy " + this.lastSavedPolicyName
                + ": tenant=" + tenant + " geography=" + geography + " market=" + market
                + " lob=" + lob + " channel=" + channel);
    }

    @Given("the policy has composition {string} referencing {string} and {string}")
    public void updatePolicyComposition2(String operator, String ref1, String ref2) {
        updateLastSavedPolicy(policy -> {
            Map<String, Object> comp = new LinkedHashMap<>();
            comp.put("operator", operator);
            comp.put("policies", List.of(ref1, ref2));
            policy.put("composition", comp);
        });
        screenCapture.log("Set composition on policy " + this.lastSavedPolicyName
                + ": " + operator + " [" + ref1 + ", " + ref2 + "]");
    }

    @Given("the policy has composition {string} referencing {string}")
    public void updatePolicyComposition1(String operator, String ref1) {
        updateLastSavedPolicy(policy -> {
            Map<String, Object> comp = new LinkedHashMap<>();
            comp.put("operator", operator);
            comp.put("policies", List.of(ref1));
            policy.put("composition", comp);
        });
        screenCapture.log("Set composition on policy " + this.lastSavedPolicyName
                + ": " + operator + " [" + ref1 + "]");
    }

    @Given("the policy {string} has a certification nextCertificationDate {string} lastCertifiedBy {string} lastCertifiedAt {string}")
    public void updatePolicyCertification(String policyName, String nextCertificationDate,
                                          String lastCertifiedBy, String lastCertifiedAt) {
        updatePolicyCertification(policyName, nextCertificationDate, lastCertifiedBy, lastCertifiedAt, null);
    }

    @Given("the policy {string} has a certification nextCertificationDate {string} lastCertifiedBy {string} lastCertifiedAt {string} with a waiver expiring {string}")
    public void updatePolicyCertificationWithWaiver(String policyName, String nextCertificationDate,
                                                    String lastCertifiedBy, String lastCertifiedAt,
                                                    String waiverExpiry) {
        updatePolicyCertification(policyName, nextCertificationDate, lastCertifiedBy, lastCertifiedAt, waiverExpiry);
    }

    private void updatePolicyCertification(String policyName, String nextCertificationDate,
                                           String lastCertifiedBy, String lastCertifiedAt,
                                           String waiverExpiry) {
        Map<String, Object> cert = new LinkedHashMap<>();
        cert.put("status", "CERTIFIED");
        cert.put("nextCertificationDate", nextCertificationDate);
        cert.put("lastCertifiedBy", lastCertifiedBy);
        cert.put("lastCertifiedAt", lastCertifiedAt);
        if (waiverExpiry != null) {
            Map<String, Object> waiver = new LinkedHashMap<>();
            waiver.put("expiryDate", waiverExpiry);
            waiver.put("riskRationale", "temporary migration period while replacement policy is authored");
            waiver.put("compensatingControls", "additional monitoring and manual approval gate in place");
            cert.put("waiver", waiver);
        } else {
            cert.put("waiver", null);
        }
        org.springframework.data.mongodb.core.query.Update update =
                org.springframework.data.mongodb.core.query.Update.update("certification", cert);
        mongoTemplate.updateFirst(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("name").is(policyName)),
                update, "policies");
        screenCapture.log("Updated certification for " + policyName + ": nextCertificationDate="
                + nextCertificationDate + " waiverExpiry=" + waiverExpiry);
    }

    @Given("the policy {string} is in state {string}")
    public void updatePolicyState(String policyName, String state) {
        org.springframework.data.mongodb.core.query.Update update =
                org.springframework.data.mongodb.core.query.Update.update("state", state);
        mongoTemplate.updateFirst(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("name").is(policyName)),
                update, "policies");
        screenCapture.log("Set policy " + policyName + " state=" + state);
    }

    @Then("an audit event of type {string} should exist with severity {string} for entity {string}")
    public void verifyAuditEventExistsWithSeverity(String eventType, String severity, String entityId) {
        List<Map<String, Object>> events = fetchAuditEvents(entityId);
        boolean found = events.stream().anyMatch(event ->
                eventType.equals(event.get("eventType")) && severity.equals(event.get("severity")));
        assertThat(found).as("audit event " + eventType + " [" + severity + "] for " + entityId).isTrue();
        screenCapture.logAssertion("Audit event " + eventType + " [" + severity + "] for " + entityId,
                found, "present", found ? "found" : "not found");
    }

    @Then("no audit event of type {string} should exist for entity {string}")
    public void verifyNoAuditEvent(String eventType, String entityId) {
        List<Map<String, Object>> events = fetchAuditEvents(entityId);
        boolean found = events.stream().anyMatch(event -> eventType.equals(event.get("eventType")));
        assertThat(found).as("audit event " + eventType + " for " + entityId).isFalse();
        screenCapture.logAssertion("No audit event " + eventType + " for " + entityId,
                !found, "absent", found ? "found" : "absent");
    }

    private List<Map<String, Object>> fetchAuditEvents(String entityId) {
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort()
                + "/v1/admin/audit-events?entityId=" + entityId;
        this.auditEventsResponse = restTemplate.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        return this.auditEventsResponse.getBody() != null ? this.auditEventsResponse.getBody() : List.of();
    }

    @Given("a shadow evaluation policy with effect {string} and name {string} for action {string} and resource type {string} is saved to MongoDB")
    public void saveShadowEvaluationPolicy(String effect, String name, String action, String resourceType) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "DRAFT");
        policy.put("shadowEvaluation", true);
        policy.put("action", action);
        policy.put("resourceType", resourceType);
        mongoTemplate.save(policy, "policies");
        this.lastSavedPolicyName = name;
        screenCapture.captureSeedData("policies (shadow: " + name + ")", policy);
        screenCapture.log("Saved shadow evaluation policy: " + name + " effect=" + effect
                + " action=" + action + " resourceType=" + resourceType);
    }

    @Then("the shadow-decisions collection should contain {int} entries for policy {string}")
    public void verifyShadowDecisions(int expectedCount, String policyName) {
        verifyShadowDecisionsCount(expectedCount, policyName);
    }

    @Then("the shadow-decisions collection should contain {int} entry for policy {string}")
    public void verifyShadowDecision(int expectedCount, String policyName) {
        verifyShadowDecisionsCount(expectedCount, policyName);
    }

    private void verifyShadowDecisionsCount(int expectedCount, String policyName) {
        long count = mongoTemplate.count(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("policyId").is(policyName)),
                "shadow_decisions");
        assertThat(count).as("shadow-decisions entries for " + policyName).isEqualTo(expectedCount);
        screenCapture.logAssertion("Shadow-decisions count for " + policyName,
                count == expectedCount, String.valueOf(expectedCount), String.valueOf(count));
    }

    /** Updates the most recently saved policy document in MongoDB and logs the change. */
    private void updateLastSavedPolicy(java.util.function.Consumer<Map<String, Object>> mutator) {
        if (this.lastSavedPolicyName == null) {
            throw new IllegalStateException("No policy has been saved yet — the 'the policy has ...' step must follow a policy save step");
        }
        org.springframework.data.mongodb.core.query.Query query = org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("name").is(this.lastSavedPolicyName));
        Map<String, Object> policy = mongoTemplate.findOne(query, Map.class, "policies");
        if (policy == null) {
            throw new IllegalStateException("Policy '" + this.lastSavedPolicyName + "' not found in MongoDB");
        }
        mutator.accept(policy);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (updated: " + this.lastSavedPolicyName + ")", policy);
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
        this.runtimeContext.put(key, tryParseJson(value));
        screenCapture.log("Set runtime context: " + key + "=" + value);
    }

    /**
     * Attempts to parse a value as JSON when it looks like an array or object.
     * This enables runtime context steps to pass complex types like
     * {@code "[\"crm\", \"website\"]"} or {@code "{\"status\": \"GRANTED\"}"}
     * which are then deserialized to List/Map instead of remaining strings.
     */
    private static Object tryParseJson(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if ((trimmed.startsWith("[") && trimmed.endsWith("]"))
                || (trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(trimmed, Object.class);
            } catch (Exception e) {
                // Not valid JSON — fall through to string
            }
        }
        return value;
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
        this.lastSavedPolicyName = name;
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

    @Given("a fresh consistency token is issued for scope {string}")
    public void issueTokenForScope(String scope) {
        String token = consistencyTokenStore.issueToken(scope);
        issuedTokensByScope.put(scope, token);
        screenCapture.log("Issued consistency token for scope " + scope + ": " + token);
    }

    @Given("the request uses the issued token for scope {string}")
    public void useIssuedTokenForScope(String scope) {
        String token = issuedTokensByScope.get(scope);
        if (token == null) {
            throw new IllegalStateException("No issued token for scope " + scope);
        }
        addScopeConsistencyToken(scope, token);
    }

    @Given("a consistency token {string} for scope {string}")
    public void addScopeConsistencyToken(String token, String scope) {
        @SuppressWarnings("unchecked")
        Map<String, Object> tokens = (Map<String, Object>) this.requestBody.computeIfAbsent(
                "consistencyTokens", k -> new LinkedHashMap<String, Object>());
        tokens.put(scope, token);
        screenCapture.log("Set consistency token for scope " + scope + ": " + token);
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
        if (this.oacHealthIndicator != null) {
            this.oacHealthIndicator.setDegraded(true);
        }
        screenCapture.log("Simulated MongoDB stop: dependency flagged as unhealthy");
    }

    @Given("MongoDB is started")
    public void startMongoDB() {
        // Restore MongoDB dependency health
        this.runtimeContext.put("dependencyHealthy", true);
        if (this.oacHealthIndicator != null) {
            this.oacHealthIndicator.setDegraded(false);
        }
        screenCapture.log("Simulated MongoDB start: dependency flagged as healthy");
    }

    @Given("the decision cache is invalidated")
    public void invalidateDecisionCache() {
        if (this.decisionCache != null) {
            this.decisionCache.evictAll();
        }
        screenCapture.log("Simulated decision cache invalidation after policy update");
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

    @Given("a create policy request for effect {string} and name {string} with author {string}")
    public void prepareCreatePolicyWithAuthor(String effect, String name, String author) {
        this.requestBody.clear();
        this.requestBody.put("name", name);
        this.requestBody.put("effect", effect);
        this.requestBody.put("owner", author);
        this.requestBody.put("author", author);
        this.requestBody.put("riskLevel", "LOW");
        screenCapture.log("Prepared create-policy request: " + name + " effect=" + effect + " author=" + author);
    }

    // ==================== POLICY SPEC / CONDITION COMPOSITION STEPS ====================

    private com.oac.decision.model.PolicySpec.Builder policySpecBuilder;

    @Given("a policy spec with effect {string} and name {string}")
    public void createPolicySpec(String effect, String name) {
        this.policySpecBuilder = com.oac.decision.model.PolicySpec.builder()
                .effect(effect)
                .name(name);
        this.requestBody.clear();
        screenCapture.log("Created policy spec: " + name + " effect=" + effect);
    }

    @Given("a condition of type {string} with expression {string}")
    public void addSpelCondition(String type, String expression) {
        if ("spel".equals(type)) {
            this.policySpecBuilder.addCondition(com.oac.decision.model.PolicyCondition.spel(expression));
            screenCapture.log("Added spel condition: " + expression);
        }
    }

    @Given("a condition of type {string} with window {string} and timezone {string}")
    public void addTimeWindowCondition(String type, String window, String timezone) {
        if ("timeWindow".equals(type)) {
            this.policySpecBuilder.addCondition(com.oac.decision.model.PolicyCondition.timeWindow(window, timezone));
            screenCapture.log("Added timeWindow condition: " + window + " " + timezone);
        }
    }

    @Given("a condition of type {string} with cidr {string}")
    public void addSourceIpCondition(String type, String cidr) {
        if ("sourceIp".equals(type)) {
            this.policySpecBuilder.addCondition(com.oac.decision.model.PolicyCondition.sourceIp(cidr));
            screenCapture.log("Added sourceIp condition: " + cidr);
        }
    }

    @Given("a condition of type {string} with relationship {string}")
    public void addRebacCondition(String type, String relationship) {
        if ("rebac".equals(type)) {
            this.policySpecBuilder.addCondition(com.oac.decision.model.PolicyCondition.rebac(relationship));
            screenCapture.log("Added rebac condition: " + relationship);
        }
    }

    @Given("the policy spec conditions are saved to MongoDB")
    public void savePolicySpecToMongoDB() {
        com.oac.decision.model.PolicySpec spec = this.policySpecBuilder.build();
        java.util.Map<String, Object> doc = spec.toDocument();
        // Decision-evaluation policies must be ACTIVE to be matched by the registry;
        // the PolicySpec builder defaults to DRAFT for governance submissions.
        doc.put("state", "ACTIVE");
        mongoTemplate.save(doc, "policies");
        screenCapture.captureSeedData("policies (spec: " + spec.name() + ")", doc);
        screenCapture.log("Saved policy spec to MongoDB: " + spec.name()
                + " with " + spec.conditions().size() + " conditions");
    }

    @Given("the subject type is {string}")
    public void setPolicySpecSubjectType(String subjectType) {
        if (this.policySpecBuilder != null) {
            this.policySpecBuilder.subjectType(subjectType);
        }
        screenCapture.log("Set policy spec subject type: " + subjectType);
    }

    @Given("a flat-format policy document with effect {string} and spelCondition {string} is saved to MongoDB")
    public void saveFlatFormatPolicyWithSpel(String effect, String spelCondition) {
        java.util.Map<String, Object> policy = new java.util.LinkedHashMap<>();
        String name = "POL.FLAT.SPEL." + java.util.UUID.randomUUID().toString().substring(0, 8);
        policy.put("name", name);
        policy.put("effect", effect);
        policy.put("state", "ACTIVE");
        policy.put("spelCondition", spelCondition);
        mongoTemplate.save(policy, "policies");
        screenCapture.captureSeedData("policies (flat: " + name + ")", policy);
        screenCapture.log("Saved flat-format policy with spelCondition: " + spelCondition);
    }

    @When("the policy spec is submitted for creation via HTTP")
    public void sendPolicySpecCreateRequest() {
        com.oac.decision.model.PolicySpec spec = this.policySpecBuilder.build();
        java.util.Map<String, Object> body = spec.toDocument();
        body.put("owner", "test-owner");
        body.put("author", "test-author");
        body.put("riskLevel", "LOW");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/v1/admin/policies";
        this.response = restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        this.policyCreateResponse = this.response;
        captureRequestEvidence("POST", url, body, this.response);
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

    @When("a promote policy request is sent by principal {string} for the created policy to state {string}")
    public void sendPromotePolicyByPrincipal(String principal, String targetState) {
        if (this.policyCreateResponse == null || this.policyCreateResponse.getBody() == null) {
            throw new IllegalStateException("No created policy to promote");
        }
        String policyId = (String) this.policyCreateResponse.getBody().get("policyId");
        Map<String, Object> promoteBody = new LinkedHashMap<>();
        promoteBody.put("targetState", targetState);
        promoteBody.put("approvers", List.of("approver-1", "approver-2"));
        promoteBody.put("changeRationale", "Test promotion by " + principal);
        promoteBody.put("rollbackReference", "v1");
        promoteBody.put("principal", principal);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(promoteBody, headers);
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/v1/admin/policies/" + policyId + "/promote";
        this.response = restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        captureRequestEvidence("POST", url, promoteBody, this.response);
    }

    @When("the same check permission request is repeated {int} times")
    public void repeatCheckPermission(int times) {
        for (int i = 0; i < times; i++) {
            sendCheckPermissionRequest();
        }
    }

    @When("{int} check permission requests are sent via HTTP to open circuit breaker")
    public void sendMultipleRequestsToOpenCircuit(int count) {
        for (int i = 0; i < count; i++) {
            sendCheckPermissionRequest();
        }
    }

    @When("response should include circuit breaker {string}")
    public void verifyCircuitBreakerState(String expectedState) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        String circuitBreakerState = (String) body.get("circuitBreakerState");
        assertThat(circuitBreakerState).isEqualTo(expectedState);
        screenCapture.logAssertion("Circuit breaker state", expectedState.equals(circuitBreakerState),
                expectedState, circuitBreakerState);
    }

    @When("the health endpoint is queried")
    public void queryHealthEndpoint() {
        String url = "http://localhost:" + CucumberSpringConfiguration.getPort() + "/actuator/health";
        this.response = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        captureRequestEvidence("GET", url, null, this.response);
    }

    @When("the decision cache TTL is expired")
    public void expireDecisionCacheTtl() {
        // Signal cache expiry through runtime context (in a real implementation,
        // this would call a test-only cache eviction endpoint or set clock forward)
        this.runtimeContext.put("cacheTtlExpired", "true");
        screenCapture.log("Simulated decision cache TTL expiry");
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

    @Then("the response should include consistency tokens for scopes {string} and {string}")
    public void verifyResponseConsistencyTokens(String scope1, String scope2) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> tokens = (Map<String, Object>) body.get("consistencyTokens");
        assertThat(tokens).as("consistency token vector").containsKeys(scope1, scope2);
        screenCapture.logAssertion("Response consistency tokens include "
                + scope1 + " and " + scope2, tokens != null && tokens.containsKey(scope1) && tokens.containsKey(scope2),
                scope1 + ", " + scope2, String.valueOf(tokens));
    }

    @Then("the response should include consistency tokens for scopes {string}")
    public void verifyResponseConsistencyToken(String scope1) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> tokens = (Map<String, Object>) body.get("consistencyTokens");
        assertThat(tokens).as("consistency token vector").containsKey(scope1);
        screenCapture.logAssertion("Response consistency tokens include " + scope1,
                tokens != null && tokens.containsKey(scope1), scope1, String.valueOf(tokens));
    }

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

    @Then("the response should include header {string} with value {string}")
    public void verifyResponseHeader(String headerName, String expectedValue) {
        HttpHeaders headers = this.response.getHeaders();
        assertThat(headers).isNotNull();
        List<String> headerValues = headers.get(headerName);
        assertThat(headerValues).isNotEmpty();
        String actualValue = headerValues.get(0);
        assertThat(actualValue).isEqualTo(expectedValue);
        screenCapture.logAssertion("Response header: " + headerName, expectedValue.equals(actualValue),
                expectedValue, actualValue);
    }

    @Then("the health response should include component {string} with status {string}")
    public void verifyHealthComponentStatus(String componentName, String expectedStatus) {
        Map<String, Object> body = this.response.getBody();
        assertThat(body).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) body.get("components");
        assertThat(components).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> component = (Map<String, Object>) components.get(componentName);
        assertThat(component).isNotNull();
        String actualStatus = (String) component.get("status");
        assertThat(actualStatus).isEqualTo(expectedStatus);
        screenCapture.logAssertion("Health component: " + componentName, expectedStatus.equals(actualStatus),
                expectedStatus, actualStatus);
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