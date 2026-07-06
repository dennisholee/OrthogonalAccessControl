package com.oac.decision.e2e;

import com.oac.decision.adapter.out.mongodb.MongoQueryFilterGenerator;
import com.oac.decision.application.port.out.RelationshipGraphPort;
import com.oac.decision.model.*;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test for ReBAC + MongoDB.
 * Validates: policy creation → relationship storage → decision evaluation →
 * field-level access map generation → MongoDB query filter output.
 *
 * Uses Testcontainers for fully automated CI-compatible testing.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
@ActiveProfiles("mongodb")
class MongoDbReBacIntegrationIT {

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RelationshipGraphPort relationshipGraphPort;

    @BeforeEach
    void setUp() {
        // Clean collections before each test
        mongoTemplate.dropCollection("relationships");
        mongoTemplate.dropCollection("policies");
        mongoTemplate.dropCollection("resource_grants");
    }

    @Test
    void shouldCreateRelationshipAndEvaluateReBACDecision() {
        // Given: a relationship edge between user-1 and acc-1 as owner
        var boundary = new BoundaryContext("tenant-a", "us", "retail", "cards", "staff");
        var edge = new RelationshipEdge(
                null, "user-1", "human", "owner",
                "acc-1", "account", boundary,
                Instant.now().minusSeconds(3600), null, Map.of()
        );

        String edgeId = relationshipGraphPort.createRelationship(edge);
        assertThat(edgeId).isNotBlank();

        // When: checking relationship existence
        boolean hasRelationship = relationshipGraphPort.hasRelationship(
                "user-1", "acc-1", null, 3
        );

        // Then: relationship should be found
        assertThat(hasRelationship).isTrue();
    }

    @Test
    void shouldNotFindRevokedRelationship() {
        // Given: a relationship edge that is then revoked
        var boundary = new BoundaryContext("tenant-a", "us", "retail", "cards", "staff");
        var edge = new RelationshipEdge(
                null, "user-2", "human", "reviewer",
                "acc-2", "account", boundary,
                Instant.now().minusSeconds(3600), null, Map.of()
        );

        String edgeId = relationshipGraphPort.createRelationship(edge);
        relationshipGraphPort.revokeRelationship(edgeId);

        // When: checking relationship after revocation
        boolean hasRelationship = relationshipGraphPort.hasRelationship(
                "user-2", "acc-2", null, 3
        );

        // Then: relationship should not be found
        assertThat(hasRelationship).isFalse();
    }

    @Test
    void shouldTraverseUpTo3Hops() {
        // Given: a 3-hop relationship chain: user-3 -> resource-A -> user-4 -> resource-B
        var boundary = new BoundaryContext("tenant-a", "us", "retail", "cards", "staff");
        relationshipGraphPort.createRelationship(new RelationshipEdge(
                null, "user-3", "human", "member",
                "team-1", "team", boundary,
                Instant.now().minusSeconds(3600), null, Map.of()
        ));
        relationshipGraphPort.createRelationship(new RelationshipEdge(
                null, "user-4", "human", "member",
                "team-1", "team", boundary,
                Instant.now().minusSeconds(3600), null, Map.of()
        ));
        relationshipGraphPort.createRelationship(new RelationshipEdge(
                null, "user-4", "human", "owner",
                "resource-B", "document", boundary,
                Instant.now().minusSeconds(3600), null, Map.of()
        ));

        // When: traversing from user-3 with depth 3
        Set<String> resources = relationshipGraphPort.traverseResources("user-3", null, 3);

        // Then: should reach resource-B through team-1 and user-4 (2 hops)
        assertThat(resources).contains("resource-B");
    }

    @Test
    void shouldGenerateMongoQueryFiltersFromAttributeAccessMap() {
        // Given: an attribute access map with field-level constraints
        var accessMap = new AttributeAccessMap(
                Map.of("ssn", AttributeAccessLevel.MASK,
                       "internalNotes", AttributeAccessLevel.HIDDEN,
                       "accountNumber", AttributeAccessLevel.READ),
                Map.of("PII", AttributeAccessLevel.MASK)
        );

        // When: generating MongoDB query filters
        Document projection = MongoQueryFilterGenerator.buildFieldProjection(accessMap);
        Document maskStage = MongoQueryFilterGenerator.buildMaskStage(accessMap);
        Document resourceFilter = MongoQueryFilterGenerator.buildResourceFilter(List.of("doc-1", "doc-2"));
        var pipeline = MongoQueryFilterGenerator.buildAuthorizationPipeline(
                List.of("doc-1"), new BoundaryContext("tenant-a", "us", "retail", "cards", "staff"), accessMap);

        // Then: filters should be correctly generated
        assertThat(projection).containsKey("ssn");
        assertThat(maskStage).containsKey("ssn");
        Object ssnValue = maskStage.get("ssn");
        assertThat(ssnValue).isInstanceOf(Document.class);
        assertThat(resourceFilter).isNotNull();
        Document idFilter = resourceFilter.get("_id", Document.class);
        assertThat(idFilter).isNotNull();
        Object inValues = idFilter.get("$in");
        assertThat(inValues).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> inList = (List<String>) inValues;
        assertThat(inList).contains("doc-1", "doc-2");
        assertThat(pipeline).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldEvaluateExpiredRelationshipAsInactive() {
        // Given: an expired relationship edge
        var boundary = new BoundaryContext("tenant-a", "us", "retail", "cards", "staff");
        var edge = new RelationshipEdge(
                null, "user-5", "human", "delegate",
                "acc-5", "account", boundary,
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600), // expired 1 hour ago
                Map.of()
        );

        String edgeId = relationshipGraphPort.createRelationship(edge);
        assertThat(edgeId).isNotBlank();

        // When: checking for active relationship
        boolean hasActiveRelationship = relationshipGraphPort.hasRelationship(
                "user-5", "acc-5", null, 3
        );

        // Then: should not find expired relationship
        assertThat(hasActiveRelationship).isFalse();
    }

    @Test
    void shouldStoreAndRetrievePolicyDefinitions() {
        // Given: a policy document stored in MongoDB
        Document policy = new Document()
                .append("name", "POL.REBAC.ACCOUNT.OWNER.READ.ALLOW.v1")
                .append("state", "ACTIVE")
                .append("effect", "ALLOW")
                .append("action", "read")
                .append("resourceType", "account")
                .append("subjectId", "user-1")
                .append("version", "v1")
                .append("boundaryContext", new Document()
                        .append("tenant", "tenant-a")
                        .append("geography", "us")
                        .append("market", "retail")
                        .append("lineOfBusiness", "cards")
                        .append("channel", "staff"));

        mongoTemplate.save(policy, "policies");

        // When: querying active policies
        List<Document> activePolicies = mongoTemplate.find(
                Query.query(Criteria.where("state").is("ACTIVE")),
                Document.class, "policies");

        // Then: policy should be retrievable
        assertThat(activePolicies).hasSize(1);
        assertThat(activePolicies.get(0).getString("name"))
                .isEqualTo("POL.REBAC.ACCOUNT.OWNER.READ.ALLOW.v1");
    }
}