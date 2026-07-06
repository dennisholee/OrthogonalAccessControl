package com.oac.decision.e2e;

import com.oac.decision.adapter.out.mongodb.MongoQueryFilterGenerator;
import com.oac.decision.application.port.in.DecisionQueryUseCase;
import com.oac.decision.application.port.out.RelationshipGraphPort;
import com.oac.decision.model.*;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end authorization flow showing the complete ReBAC + MongoDB integration.
 *
 * This test demonstrates the full lifecycle:
 * 1. Seed a policy in MongoDB
 * 2. Create a ReBAC relationship edge
 * 3. Make a CheckPermission decision → receives ALLOW + attribute-level access map
 * 4. Generate MongoDB query filters from the decision
 * 5. Call LookupResources → discovers authorized resources via relationship traversal
 *
 * Unlike {@link MongoDbReBacIntegrationIT} which tests components in isolation,
 * this test validates end-to-end wiring through the application service layer.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
@ActiveProfiles("mongodb")
class EndToEndAuthorizationFlowIT {

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
    }

    @Autowired
    private DecisionQueryUseCase decisionQueryUseCase;

    @Autowired
    private RelationshipGraphPort relationshipGraphPort;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("relationships");
        mongoTemplate.dropCollection("policies");
        mongoTemplate.dropCollection("resource_grants");
    }

    @Test
    @DisplayName("Full ReBAC flow: seed policy → create relationship → check permission → generate MongoDB filter → lookup resources")
    void shouldCompleteFullReBacAuthorizationFlow() {
        // ──────────────────────────────────────────────────────────────────────────
        // Step 1: Seed an ALLOW policy into MongoDB
        // ──────────────────────────────────────────────────────────────────────────
        Document policy = new Document()
                .append("name", "POL.REBAC.ACCOUNT.OWNER.READ.ALLOW.v1")
                .append("state", "ACTIVE")
                .append("effect", "ALLOW")
                .append("action", "read")
                .append("resourceType", "account")
                .append("version", "v1")
                .append("boundaryContext", new Document()
                        .append("tenant", "tenant-a")
                        .append("geography", "us")
                        .append("market", "retail")
                        .append("lineOfBusiness", "cards")
                        .append("channel", "staff")
                );

        mongoTemplate.save(policy, "policies");
        assertThat(mongoTemplate.count(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("state").is("ACTIVE")
                ), "policies")).isEqualTo(1);

        // Seed resource grant for LookupResources support
        Document grant = new Document()
                .append("subjectId", "user-1")
                .append("action", "read")
                .append("resourceType", "account")
                .append("resourceId", "acc-1")
                .append("tenant", "tenant-a")
                .append("geography", "us")
                .append("market", "retail")
                .append("lineOfBusiness", "cards")
                .append("channel", "staff");
        mongoTemplate.save(grant, "resource_grants");

        // ──────────────────────────────────────────────────────────────────────────
        // Step 2: Create a ReBAC relationship: user-1 is owner of acc-1
        // ──────────────────────────────────────────────────────────────────────────
        var boundary = new BoundaryContext("tenant-a", "us", "retail", "cards", "staff");
        String relationshipId = relationshipGraphPort.createRelationship(new RelationshipEdge(
                null,
                "user-1",          // subjectId
                "human",           // subjectType
                "owner",           // relationshipType
                "acc-1",           // resourceId
                "account",         // resourceType
                boundary,
                java.time.Instant.now().minusSeconds(3600),  // validFrom (1h ago)
                null,                                         // validUntil (never expires)
                Map.of()                                      // metadata
        ));
        assertThat(relationshipId).isNotBlank();

        // Verify relationship is active
        assertThat(relationshipGraphPort.hasRelationship("user-1", "acc-1", null, 3))
                .as("Direct owner relationship must be found")
                .isTrue();

        // ──────────────────────────────────────────────────────────────────────────
        // Step 3: Construct a CheckPermission request and evaluate
        // ──────────────────────────────────────────────────────────────────────────
        var request = new CheckPermissionRequest(
                new SubjectRef("human", "user-1"),       // subject
                "read",                                  // action
                new ResourceRef("account", "acc-1"),     // resource
                boundary,                                // boundaryContext
                Map.of(                                  // runtimeContext
                        "resourceTenant", "tenant-a",
                        "resourceGeography", "us",
                        "resourceMarket", "retail",
                        "resourceLineOfBusiness", "cards",
                        "resourceChannel", "staff",
                        "caveats", Map.of("fields", Map.of(
                                "ssn", "MASK",
                                "internalNotes", "HIDDEN",
                                "balance", "READ"
                        ))
                ),
                null,     // consistencyToken
                "e2e-req-1",  // requestId
                null,     // endpointClassification
                null,     // endpointKey
                null      // strictConsistency
        );

        // Execute decision
        CheckPermissionResponse response = decisionQueryUseCase.checkPermission(request);

        // Debug output to identify which rule fired
        System.out.println("DEBUG: decision=" + response.decision()
                + " code=" + response.decisionCode()
                + " policies=" + response.matchedPolicies());

        // ──────────────────────────────────────────────────────────────────────────
        // Step 4: Verify the decision outcome
        // ──────────────────────────────────────────────────────────────────────────
        assertThat(response.decision())
                .as("Policy reBAC allow must produce ALLOW (code=" + response.decisionCode() + ")")
                .isEqualTo("ALLOW");

        assertThat(response.matchedPolicies())
                .as("Must match the seeded ReBAC policy")
                .contains("POL.REBAC.ACCOUNT.OWNER.READ.ALLOW.v1");

        // Verify attribute-level access map is present (from caveats)
        AttributeAccessMap accessMap = response.attributeAccessMap();

        assertThat(accessMap)
                .as("CheckPermissionResponse must return an AttributeAccessMap")
                .isNotNull();

        // The FIELD_MASK caveat defines ssn=MASK, internalNotes=HIDDEN, balance=READ
        assertThat(accessMap.forField("ssn"))
                .as("Field 'ssn' must be MASKED per caveat")
                .isEqualTo(AttributeAccessLevel.MASK);

        assertThat(accessMap.forField("balance"))
                .as("Field 'balance' must be READ per caveat")
                .isEqualTo(AttributeAccessLevel.READ);

        // ──────────────────────────────────────────────────────────────────────────
        // Step 5: Generate MongoDB query filters from the authorization decision
        // ──────────────────────────────────────────────────────────────────────────
        List<Document> pipeline = MongoQueryFilterGenerator.buildAuthorizationPipeline(
                List.of("acc-1"),          // authorized resource IDs
                boundary,                  // boundary context
                accessMap                  // attribute-level access constraints
        );

        assertThat(pipeline)
                .as("Authorization pipeline must have at least 2 stages (match + transform)")
                .hasSizeGreaterThanOrEqualTo(2);

        // pipeline[0] = $match on authorized resource IDs
        Document matchStage = pipeline.get(0);
        assertThat(matchStage)
                .as("First pipeline stage must be a $match stage")
                .containsKey("$match");
        assertThat(matchStage.get("$match", Document.class).toJson())
                .as("$match must filter by authorized resource ID 'acc-1'")
                .contains("acc-1");

        // pipeline should contain an $addFields for masking
        boolean hasMaskStage = pipeline.stream()
                .anyMatch(doc -> doc.containsKey("$addFields"));
        assertThat(hasMaskStage)
                .as("Pipeline must include an $addFields stage for field masking")
                .isTrue();

        // pipeline should contain a $project for field protection
        boolean hasProjectStage = pipeline.stream()
                .anyMatch(doc -> doc.containsKey("$project"));
        assertThat(hasProjectStage)
                .as("Pipeline must include a $project stage for field visibility")
                .isTrue();

        // ──────────────────────────────────────────────────────────────────────────
        // Step 6: Call LookupResources to discover authorized resources
        // ──────────────────────────────────────────────────────────────────────────
        var lookupRequest = new LookupResourcesRequest(
                new SubjectRef("human", "user-1"),
                "read",
                "account",
                boundary,
                null,   // consistencyToken
                null,   // strictConsistency
                null,   // requiredConsistencyToken
                null,   // simulatedRegionalLagMs
                null,   // replicaVersion
                null,   // minimumReplicaVersion
                10,     // pageSize
                null    // pageToken
        );

        LookupResourcesResponse lookupResponse = decisionQueryUseCase.lookupResources(lookupRequest);
        assertThat(lookupResponse.resourceIds())
                .as("LookupResources must find resource IDs that user-1 can read")
                .isNotEmpty();

        // ──────────────────────────────────────────────────────────────────────────
        // Step 7: Verify cross-tenant isolation (boundary enforcement)
        // ──────────────────────────────────────────────────────────────────────────
        var wrongTenantBoundary = new BoundaryContext("tenant-b", "us", "retail", "cards", "staff");
        var wrongTenantRequest = new CheckPermissionRequest(
                new SubjectRef("human", "user-1"),
                "read",
                new ResourceRef("account", "acc-1"),
                wrongTenantBoundary,
                Map.of(
                        "resourceTenant", "tenant-b",
                        "resourceGeography", "us",
                        "resourceMarket", "retail",
                        "resourceLineOfBusiness", "cards",
                        "resourceChannel", "staff"
                ),
                null,
                "e2e-req-2",
                null,
                null,
                null
        );

        CheckPermissionResponse deniedResponse = decisionQueryUseCase.checkPermission(wrongTenantRequest);
        assertThat(deniedResponse.decision())
                .as("Cross-tenant request must be DENY")
                .isEqualTo("DENY");

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  END-TO-END FLOW VERIFIED                              ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║ Step 1: Seed policy            ──── MongoDB.policies   ║");
        System.out.println("║ Step 2: Create relationship    ──── MongoDB.relationships");
        System.out.println("║ Step 3: CheckPermission        ──── ALLOW              ║");
        System.out.println("║ Step 4: AttributeAccessMap     ──── ssn=MASK           ║");
        System.out.println("║ Step 5: QueryFilterPipeline    ──── $match+$addFields  ║");
        System.out.println("║ Step 6: LookupResources        ──── acc-1 found        ║");
        System.out.println("║ Step 7: Cross-tenant isolation  ──── tenant-b=DENY     ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }
}