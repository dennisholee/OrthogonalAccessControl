package com.oac.emulator;

import com.oac.decision.adapter.out.policy.MongoPolicyRegistryAdapter;
import com.oac.decision.adapter.out.relationship.MongoRelationshipGraphAdapter;
import com.oac.decision.model.BoundaryContext;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.ResourceRef;
import com.oac.decision.model.SubjectRef;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calibration contract test — guarantees the standalone emulator's in-memory adapters
 * produce byte-for-byte identical decisions to the core MongoDB-backed adapters for the
 * same policy and relationship inputs.
 * <p>
 * Both adapter pairs now delegate to the same shared logic:
 * <ul>
 *   <li>{@code PolicyMatcher} — the matching passes (strict, subject DENY, broad DENY,
 *       SpEL, conditions[]) plus boundary/array-in semantics</li>
 *   <li>{@code RelationshipBfsEvaluator} — the bounded BFS traversal semantics</li>
 * </ul>
 * The single divergence is {@code MongoPolicyRegistryAdapter.applyBaselineRules}, a set of
 * BDD test hacks that is deliberately NOT replicated in the emulator; this test seeds
 * neutral subjects/tenants so the baseline never triggers.
 */
@Testcontainers
class AdapterCalibrationContractTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static MongoTemplate mongoTemplate;
    private static MongoPolicyRegistryAdapter mongoPolicyAdapter;
    private static MongoRelationshipGraphAdapter mongoGraphAdapter;

    private List<Map<String, Object>> seedPolicies;
    private List<Map<String, Object>> seedRelationships;

    private static final SubjectRef CAL_USER = new SubjectRef("human", "cal-user");
    private static final SubjectRef BLOCKED_USER = new SubjectRef("human", "blocked-cal-user");
    private static final ResourceRef ORDER = new ResourceRef("order", "ORD-001");
    private static final ResourceRef ACCOUNT = new ResourceRef("account", "ACC-001");
    private static final BoundaryContext BOUNDARY = new BoundaryContext(
            "cal-tenant", "EU", "enterprise", "retail", "staff");

    @BeforeAll
    static void startMongo() {
        mongoTemplate = new MongoTemplate(
                MongoClients.create(MONGO.getConnectionString()), "calibration");
        mongoPolicyAdapter = new MongoPolicyRegistryAdapter(mongoTemplate);
        mongoGraphAdapter = new MongoRelationshipGraphAdapter(mongoTemplate);
    }

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("policies");
        mongoTemplate.dropCollection("relationships");
        mongoTemplate.dropCollection("policy_sets");
        seedPolicies = canonicalPolicies();
        seedRelationships = canonicalRelationships();
    }

    @Test
    void matchedPoliciesAreIdenticalAcrossAdapters() {
        for (Map<String, Object> policy : seedPolicies) {
            mongoTemplate.save(copy(policy), "policies");
        }
        List<Map<String, Object>> seedPolicySets = canonicalPolicySets();
        for (Map<String, Object> set : seedPolicySets) {
            mongoTemplate.save(copy(set), "policy_sets");
        }

        InMemoryPolicyRegistryAdapter memoryAdapter = new InMemoryPolicyRegistryAdapter(seedPolicies);
        memoryAdapter.setPolicySets(seedPolicySets);

        for (CheckPermissionRequest request : canonicalRequests()) {
            List<String> mongo = mongoPolicyAdapter.findMatchedPolicies(request);
            List<String> memory = memoryAdapter.findMatchedPolicies(request);
            assertThat(memory)
                    .as("matchedPolicies for %s %s %s boundary=%s",
                            request.subject().id(), request.action(), request.resource(),
                            request.boundaryContext())
                    .containsExactlyElementsOf(mongo);
        }
    }

    @Test
    void relationshipLookupsAreIdenticalAcrossAdapters() {
        for (Map<String, Object> rel : seedRelationships) {
            mongoTemplate.save(copy(rel), "relationships");
        }

        InMemoryRelationshipGraphAdapter memoryAdapter =
                new InMemoryRelationshipGraphAdapter(seedRelationships);

        for (Object[] probe : relationshipProbes()) {
            String subjectId = (String) probe[0];
            String resourceId = (String) probe[1];
            String type = (String) probe[2];
            int maxDepth = (int) probe[3];
            boolean mongo = mongoGraphAdapter.hasRelationship(subjectId, resourceId, type, maxDepth);
            boolean memory = memoryAdapter.hasRelationship(subjectId, resourceId, type, maxDepth);
            assertThat(memory)
                    .as("hasRelationship(%s, %s, %s, %d)", subjectId, resourceId, type, maxDepth)
                    .isEqualTo(mongo);
        }
    }

    @Test
    void boundaryScopedRelationshipLookupsAreIdenticalAcrossAdapters() {
        for (Map<String, Object> rel : seedRelationships) {
            mongoTemplate.save(copy(rel), "relationships");
        }

        InMemoryRelationshipGraphAdapter memoryAdapter =
                new InMemoryRelationshipGraphAdapter(seedRelationships);

        Map<String, String> retailScope = Map.of("market", "retail", "lineOfBusiness", "cards");
        Map<String, String> enterpriseScope = Map.of("market", "enterprise", "lineOfBusiness", "wealth");

        // Matching scope → both should traverse
        boolean mongoMatch = mongoGraphAdapter.hasRelationship(
                "retail-manager", "PARTY-1", "member_of", 3, retailScope);
        boolean memoryMatch = memoryAdapter.hasRelationship(
                "retail-manager", "PARTY-1", "member_of", 3, retailScope);
        assertThat(memoryMatch).as("matching retail scope traverses PARTY-1").isEqualTo(mongoMatch).isTrue();

        // Non-matching scope → both should NOT traverse
        boolean mongoNoMatch = mongoGraphAdapter.hasRelationship(
                "retail-manager", "PARTY-2", "member_of", 3, retailScope);
        boolean memoryNoMatch = memoryAdapter.hasRelationship(
                "retail-manager", "PARTY-2", "member_of", 3, retailScope);
        assertThat(memoryNoMatch).as("retail scope must not traverse enterprise-scoped edge")
                .isEqualTo(mongoNoMatch).isFalse();

        // Enterprise scope finds its own edge
        boolean mongoEnt = mongoGraphAdapter.hasRelationship(
                "retail-manager", "PARTY-2", "member_of", 3, enterpriseScope);
        boolean memoryEnt = memoryAdapter.hasRelationship(
                "retail-manager", "PARTY-2", "member_of", 3, enterpriseScope);
        assertThat(memoryEnt).as("enterprise scope traverses enterprise edge")
                .isEqualTo(mongoEnt).isTrue();
    }

    // -------- canonical seeds --------

    private List<Map<String, Object>> canonicalPolicies() {
        List<Map<String, Object>> policies = new ArrayList<>();

        // Query 1: strict match on action + resourceType + full boundary
        policies.add(policy("POL.ALLOW.GENERAL.v1", "ALLOW", "read", "order", null)
                .and("tenant", "cal-tenant").and("geography", "EU")
                .and("market", "enterprise").and("lineOfBusiness", "retail")
                .and("channel", "staff").build());

        // Array `in` semantics: multi-value boundary field
        policies.add(policy("POL.ALLOW.MULTI.CHANNEL.v1", "ALLOW", "*", "order", null)
                .and("channel", List.of("staff", "web")).build());

        // Wildcard action + resourceType
        policies.add(policy("POL.ALLOW.WILDCARD.v1", "ALLOW", "*", "*", null).build());

        // Query 2: subject-scoped DENY (no action constraint)
        policies.add(policy("POL.DENY.SUBJECT.v1", "DENY", null, null, "blocked-cal-user").build());

        // Query 2b: broad DENY (no action, no subjectId)
        policies.add(policy("POL.DENY.BROAD.v1", "DENY", null, null, null).build());

        // Query 3: SpEL condition
        policies.add(policy("POL.ALLOW.SPEL.v1", "ALLOW", "read", "account", null)
                .and("spelCondition", "#runtime['amount'] > 1000").build());

        // Query 4: conditions[] array
        Map<String, Object> cond = policy("POL.ALLOW.COND.v1", "ALLOW", "read", "order", null).build();
        cond.put("conditions", List.of(Map.of(
                "type", "spel", "params", Map.of("expression", "#runtime['flag'] == true"))));
        policies.add(cond);

        // Optional boundary dimensions: purpose (array) and regulatoryRegime
        policies.add(policy("POL.ALLOW.PURPOSE.v1", "ALLOW", "read", "order", null)
                .and("purpose", List.of("marketing", "sales")).build());
        policies.add(policy("POL.ALLOW.REGIME.v1", "ALLOW", "read", "account", null)
                .and("regulatoryRegime", "GDPR").build());

        // DENY with action constraint must NOT surface via Query 2/2b; only via Query 1 strict match
        policies.add(policy("POL.DENY.SUBJECT.ACTION.v1", "DENY", "delete", "order",
                "blocked-cal-user").build());

        // Effective-period policies (Section 4.4)
        policies.add(policy("POL.ALLOW.EXPIRED.v1", "ALLOW", "read", "order", null)
                .and("effectiveUntil", "2020-01-01T00:00:00Z").build());
        policies.add(policy("POL.ALLOW.SCHEDULED.v1", "ALLOW", "read", "order", null)
                .and("effectiveFrom", "2099-01-01T00:00:00Z").build());
        policies.add(policy("POL.ALLOW.ACTIVE.WINDOW.v1", "ALLOW", "read", "order", null)
                .and("effectiveFrom", "2020-01-01T00:00:00Z")
                .and("effectiveUntil", "2099-01-01T00:00:00Z").build());

        // Policy Set members (Section 4.2) — evaluated only through their set
        policies.add(policy("POL.SET.MEMBER.ALLOW.v1", "ALLOW", "read", "order", null).build());
        policies.add(policy("POL.SET.MEMBER.DENY.v1", "DENY", "read", "order", null).build());

        return policies;
    }


    private List<Map<String, Object>> canonicalPolicySets() {
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("setId", "SET-CAL-1");
        set.put("name", "Calibration Deny-Overrides Set");
        set.put("combiningAlgorithm", "denyOverrides");
        set.put("policyIds", List.of("POL.SET.MEMBER.ALLOW.v1", "POL.SET.MEMBER.DENY.v1"));
        set.put("version", 1);
        return List.of(set);
    }


    private List<Map<String, Object>> canonicalRelationships() {
        List<Map<String, Object>> rels = new ArrayList<>();
        rels.add(relationship("CEO", "VP", "manages"));
        rels.add(relationship("VP", "employee", "manages"));
        rels.add(relationship("employee", "ORD-999", "owner"));
        rels.add(relationship("employee", "ORD-EX", "owner",
                Instant.now().minus(1, ChronoUnit.DAYS).toString()));
        // Boundary-scoped relationship (Section 4.36 composable domains)
        Map<String, Object> scoped = relationship("retail-manager", "PARTY-1", "member_of");
        scoped.put("boundaryScope", Map.of("market", "retail", "lineOfBusiness", "cards"));
        rels.add(scoped);
        // Same principal, different scope → must not be traversed by retail-scoped policy
        Map<String, Object> scoped2 = relationship("retail-manager", "PARTY-2", "member_of");
        scoped2.put("boundaryScope", Map.of("market", "enterprise", "lineOfBusiness", "wealth"));
        rels.add(scoped2);
        return rels;
    }

    // -------- canonical requests / probes --------

    private List<CheckPermissionRequest> canonicalRequests() {
        List<CheckPermissionRequest> requests = new ArrayList<>();

        // A: base request — strict match + broad DENY + conditions[]
        requests.add(request(CAL_USER, "read", ORDER, BOUNDARY, Map.of("flag", true)));

        // B: blocked subject deleting an order — subject DENY + strict DENY with action
        requests.add(request(BLOCKED_USER, "delete", ORDER, BOUNDARY, Map.of()));

        // C: reading an account — SpEL policy + regime policy
        requests.add(request(CAL_USER, "read", ACCOUNT, BOUNDARY, Map.of("amount", 5000)));

        // D: purpose-declared request — purpose array `in` semantics
        requests.add(request(CAL_USER, "read", ORDER, BoundaryContext.of(
                "cal-tenant", "EU", "enterprise", "retail", "staff", "marketing", null), Map.of()));

        // E: purpose mismatch — legal not in [marketing, sales]; regime declared
        requests.add(request(BLOCKED_USER, "read", ACCOUNT, BoundaryContext.of(
                "cal-tenant", "EU", "enterprise", "retail", "staff", "legal", "GDPR"), Map.of()));

        // F: channel web hits the multi-value array scoping
        requests.add(request(CAL_USER, "read", ORDER, BoundaryContext.of(
                "cal-tenant", "EU", "enterprise", "retail", "web", null, null), Map.of()));

        // G: set-only policies — matched solely through the denyOverrides policy set
        requests.add(request(new SubjectRef("human", "set-cal-user"), "read", ORDER, BOUNDARY, Map.of()));

        return requests;
    }


    private List<Object[]> relationshipProbes() {
        return List.of(
                new Object[]{"CEO", "VP", "manages", 3},        // direct type-scoped edge
                new Object[]{"CEO", "ORD-999", "owner", 3},     // chain through 3 hops
                new Object[]{"CEO", "ORD-777", "owner", 3},     // unreachable
                new Object[]{"employee", "ORD-EX", "owner", 3}, // expired edge skipped
                new Object[]{"CEO", "ORD-999", "owner", 0},     // default max depth
                new Object[]{"CEO", "ORD-999", "owner", 2},     // shallow depth
                new Object[]{"VP", "CEO", "manages", 3}         // reverse-edge traversal
        );
    }

    // -------- builders --------

    private static CheckPermissionRequest request(SubjectRef subject, String action, ResourceRef resource,
                                                 BoundaryContext boundary, Map<String, Object> runtime) {
        return new CheckPermissionRequest(subject, action, resource, boundary, runtime,
                null, null, null, null, null);
    }

    private static MapBuilder policy(String name, String effect, String action, String resourceType,
                                     String subjectId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("effect", effect);
        map.put("state", "ACTIVE");
        if (action != null) map.put("action", action);
        if (resourceType != null) map.put("resourceType", resourceType);
        if (subjectId != null) map.put("subjectId", subjectId);
        return new MapBuilder(map);
    }

    private static Map<String, Object> relationship(String subjectId, String resourceId, String type) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("subjectId", subjectId);
        map.put("resourceId", resourceId);
        map.put("relationshipType", type);
        return map;
    }

    private static Map<String, Object> relationship(String subjectId, String resourceId, String type, String expiresAt) {
        Map<String, Object> map = relationship(subjectId, resourceId, type);
        map.put("expiresAt", expiresAt);
        return map;
    }

    private static Map<String, Object> copy(Map<String, Object> src) {
        return new LinkedHashMap<>(src);
    }

    private static final class MapBuilder {
        private final Map<String, Object> map;

        MapBuilder(Map<String, Object> map) {
            this.map = map;
        }

        MapBuilder and(String key, Object value) {
            map.put(key, value);
            return this;
        }

        Map<String, Object> build() {
            return map;
        }
    }
}

