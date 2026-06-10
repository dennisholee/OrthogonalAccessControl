package com.oac.decision.bdd;

import com.oac.decision.adapter.out.policy.InMemoryPolicyRegistryAdapter;
import com.oac.decision.application.port.out.PolicyRegistryPort;
import com.oac.decision.application.service.DecisionApplicationService;
import com.oac.decision.model.AuditEventRecord;
import com.oac.decision.model.BoundaryContext;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.CheckPermissionResponse;
import com.oac.decision.model.ResourceRef;
import com.oac.decision.model.SubjectRef;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class PolicyDecisionSteps {

    private final List<String> matchedPolicies = new ArrayList<>();
    private final Map<String, Object> runtimeContext = new HashMap<>();

    private boolean useInMemoryRegistry;
    private SubjectRef subject;
    private String action;
    private ResourceRef resource;
    private BoundaryContext boundaryContext;
    private String consistencyToken;
    private String endpointClassification;
    private Boolean strictConsistency;
    private CheckPermissionResponse response;

    @Given("fixture-backed policy matching is used")
    public void fixtureBackedPolicyMatchingIsUsed() {
        useInMemoryRegistry = false;
    }

    @Given("in-memory policy registry is used")
    public void inMemoryPolicyRegistryIsUsed() {
        useInMemoryRegistry = true;
    }

    @And("no matched policies")
    public void noMatchedPolicies() {
        matchedPolicies.clear();
        consistencyToken = null;
        endpointClassification = null;
        strictConsistency = null;
    }

    @And("matched policies are")
    public void matchedPoliciesAre(DataTable table) {
        matchedPolicies.clear();
        List<Map<String, String>> rows = table.asMaps();
        for (Map<String, String> row : rows) {
            matchedPolicies.add(row.get("policy"));
        }
    }

    @And("subject {string} with id {string}")
    public void subjectWithId(String subjectType, String subjectId) {
        subject = new SubjectRef(subjectType, subjectId);
    }

    @And("action {string}")
    public void action(String requestAction) {
        action = requestAction;
    }

    @And("resource type {string} with id {string}")
    public void resourceTypeWithId(String resourceType, String resourceId) {
        resource = new ResourceRef(resourceType, resourceId);
    }

    @And("boundary context tenant {string} geography {string} market {string} lineOfBusiness {string} channel {string}")
    public void boundaryContext(String tenant, String geography, String market, String lineOfBusiness, String channel) {
        boundaryContext = new BoundaryContext(tenant, geography, market, lineOfBusiness, channel);
    }

    @And("runtime context entries are")
    public void runtimeContextEntriesAre(DataTable table) {
        runtimeContext.clear();
        List<Map<String, String>> rows = table.asMaps();
        for (Map<String, String> row : rows) {
            runtimeContext.put(row.get("key"), parseValue(row.get("value")));
        }
    }

    @And("consistency token {string}")
    public void consistencyToken(String token) {
        this.consistencyToken = token;
    }

    @And("strict consistency is enabled")
    public void strictConsistencyIsEnabled() {
        this.strictConsistency = Boolean.TRUE;
    }

    @And("endpoint classification {string}")
    public void endpointClassification(String classification) {
        this.endpointClassification = classification;
    }

    @When("check permission is evaluated")
    public void checkPermissionIsEvaluated() {
        PolicyRegistryPort policyRegistryPort;
        if (useInMemoryRegistry) {
            policyRegistryPort = new InMemoryPolicyRegistryAdapter();
        } else {
            List<String> snapshot = List.copyOf(matchedPolicies);
            policyRegistryPort = new PolicyRegistryPort() {
                @Override
                public List<String> findMatchedPolicies(CheckPermissionRequest request) {
                    return snapshot;
                }

                @Override
                public List<String> findAuthorizedResourceIds(com.oac.decision.model.LookupResourcesRequest request) {
                    return List.of();
                }
            };
        }

        DecisionApplicationService decisionEngine = new DecisionApplicationService(
                policyRegistryPort,
                request -> request.runtimeContext() == null ? Map.of() : request.runtimeContext(),
                new com.oac.decision.application.port.out.AuditEvidencePort() {
                    private final List<AuditEventRecord> events = new CopyOnWriteArrayList<>();

                    @Override
                    public void append(AuditEventRecord event) {
                        events.add(event);
                    }

                    @Override
                    public List<AuditEventRecord> findByEntityId(String entityId) {
                        return events.stream().filter(event -> entityId.equals(event.entityId())).toList();
                    }

                    @Override
                    public List<AuditEventRecord> findAll() {
                        return List.copyOf(events);
                    }
                },
                new com.oac.decision.application.port.out.ObservabilityPort() {
                    @Override
                    public void recordDecision(String decisionCode) {
                    }

                    @Override
                    public void recordPolicyLifecycleTransition(String fromState, String toState) {
                    }

                    @Override
                    public void recordSecurityAlert(String alertType) {
                    }

                    @Override
                    public void recordRegionalLag(String operation, long lagMs) {
                    }

                    @Override
                    public void recordReplicaVersionGap(String operation, long versionGap) {
                    }

                    @Override
                    public void recordFailoverRehearsal(boolean passed) {
                    }
                },
                endpointKey -> "account:read".equals(endpointKey) || "statement:read".equals(endpointKey),
                new com.oac.decision.application.port.out.RelationshipGraphPort() {
                    @Override
                    public List<com.oac.decision.model.RelationshipEdge> findRelationships(CheckPermissionRequest request) {
                        return List.of();
                    }
                    @Override
                    public List<String> findRelatedResourceIds(String subjectId, String resourceType, String relationshipType, int maxDepth) {
                        return List.of();
                    }
                    @Override
                    public java.util.Set<String> traverseResources(String subjectId, String resourceType, int maxDepth) {
                        return java.util.Set.of();
                    }
                    @Override
                    public String createRelationship(com.oac.decision.model.RelationshipEdge edge) {
                        return java.util.UUID.randomUUID().toString();
                    }
                    @Override
                    public void revokeRelationship(String relationshipId) {
                    }
                    @Override
                    public boolean hasRelationship(String subjectId, String resourceId, String relationshipType, int maxDepth) {
                        return false;
                    }
                    @Override
                    public int getMaxTraversalDepth() {
                        return 3;
                    }
                }
        );
        CheckPermissionRequest request = new CheckPermissionRequest(
                subject,
                action,
                resource,
                boundaryContext,
                runtimeContext.isEmpty() ? Map.of() : Map.copyOf(runtimeContext),
                consistencyToken,
                "bdd-req",
                endpointClassification,
                null,
                strictConsistency
        );

        response = decisionEngine.checkPermission(request);
    }

    @Then("decision should be {string} with code {string}")
    public void decisionShouldBeWithCode(String expectedDecision, String expectedCode) {
        assertThat(response.decision()).isEqualTo(expectedDecision);
        assertThat(response.decisionCode()).isEqualTo(expectedCode);
    }

    @And("matched policies should contain {string}")
    public void matchedPoliciesShouldContain(String expectedPolicy) {
        assertThat(response.matchedPolicies()).contains(expectedPolicy);
    }

    private Object parseValue(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return value;
    }
}
