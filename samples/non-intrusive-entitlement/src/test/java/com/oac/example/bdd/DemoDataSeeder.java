package com.oac.example.bdd;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds demo data into Testcontainers MongoDB matching the exact flat document
 * format that MongoPolicyRegistryAdapter and MongoRelationshipGraphAdapter query against.
 *
 * <p>Policy data is identical to {@code entitlement-managed-order-service}'s DemoDataSeeder
 * to ensure consistent policy evaluation across both sample projects.
 *
 * <p>Uses the Spring-managed MongoTemplate so data is written to the same database
 * that the PDP adapters read from.
 */
@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final MongoTemplate mongoTemplate;

    public DemoDataSeeder(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void seed() {
        log.info("=== Seeding demo data for test ===");
        seedPdpPolicies();
        seedPdpRelationships();
        log.info("=== Seeding complete ===");
    }

    // ----------------------------------------------------------------
    // PDP: policies collection
    // Flat documents matching MongoPolicyRegistryAdapter.findMatchedPolicies()
    // ----------------------------------------------------------------
    private void seedPdpPolicies() {
        try {
            mongoTemplate.dropCollection("policies");
        } catch (Exception e) {
            // Collection may not exist
        }

        // Policy 1: Explicit deny for attacker
        mongoTemplate.save(new Document(map(
                "name", "POL.DENY.ATTACKER.v1",
                "effect", "DENY",
                "state", "ACTIVE",
                "subjectId", "attacker"
        )), "policies");
        log.info("Seeded DENY policy for attacker");

        // Policy 2: CSR can READ orders with PII masks
        mongoTemplate.save(new Document(map(
                "name", "POL.RBAC.CSR.READ.ORDER.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "subjectId", "alice",
                "action", "read",
                "resourceType", "order",
                "tenant", "acme-corp",
                "geography", "global",
                "market", "enterprise",
                "lineOfBusiness", "ecommerce",
                "channel", "staff"
        )).append("fieldMasks", List.of(
                Map.of("field", "customer.email", "level", "MASK"),
                Map.of("field", "customer.ssn", "level", "NONE"),
                Map.of("field", "customer.name", "level", "READ")
        )), "policies");
        log.info("Seeded ALLOW policy for alice (CSR)");

        // Policy 3: Auditor can list orders without PII
        mongoTemplate.save(new Document(map(
                "name", "POL.RBAC.AUDITOR.READ.ORDERS.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "subjectId", "auditor",
                "action", "read",
                "resourceType", "order",
                "tenant", "acme-corp",
                "geography", "*",
                "market", "*",
                "lineOfBusiness", "*",
                "channel", "*"
        )).append("fieldMasks", List.of(
                Map.of("field", "customer.email", "level", "NONE"),
                Map.of("field", "customer.ssn", "level", "NONE"),
                Map.of("field", "customer.name", "level", "NONE"),
                Map.of("field", "customer.phone", "level", "NONE")
        )), "policies");
        log.info("Seeded ALLOW policy for auditor (PII fully redacted)");

        // Policy 4: Reporting service (workload) can read aggregates
        mongoTemplate.save(new Document(map(
                "name", "POL.WORKLOAD.REPORTING.READ_AGGREGATE.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "subjectId", "reporting-service",
                "action", "read_aggregate",
                "resourceType", "order",
                "tenant", "acme-corp",
                "geography", "global",
                "market", "enterprise",
                "lineOfBusiness", "ecommerce",
                "channel", "staff"
        )), "policies");
        log.info("Seeded WORKLOAD ALLOW policy for reporting-service");

        // Policy 5: ReBAC ALLOW for APPROVE
        mongoTemplate.save(new Document(map(
                "name", "POL.REBAC.MANAGER.APPROVE.ORDER.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "action", "approve",
                "resourceType", "order",
                "requiredRelationship", "manages",
                "tenant", "acme-corp",
                "geography", "global",
                "market", "enterprise",
                "lineOfBusiness", "ecommerce",
                "channel", "staff"
        )), "policies");
        log.info("Seeded ReBAC ALLOW policy for approve");

        // Policy 6: Admin full access
        mongoTemplate.save(new Document(map(
                "name", "POL.RBAC.ADMIN.FULL.ORDER.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "subjectId", "admin",
                "action", "*",
                "resourceType", "order",
                "tenant", "acme-corp",
                "channel", "staff"
        )), "policies");
        log.info("Seeded ALLOW policy for admin");
    }

    // ----------------------------------------------------------------
    // PDP: relationships collection
    // ----------------------------------------------------------------
    private void seedPdpRelationships() {
        try {
            mongoTemplate.dropCollection("relationships");
        } catch (Exception e) {
            // Collection may not exist
        }

        // bob manages ORD-001 → allow approve
        mongoTemplate.save(new Document(map(
                "subjectId", "bob",
                "resourceId", "ORD-001",
                "relationshipType", "manages"
        )), "relationships");
        log.info("Seeded ReBAC relationship: bob → ORD-001 (manages)");

        // Admin manages all orders (for ReBAC approve scenario with admin)
        mongoTemplate.save(new Document(map(
                "subjectId", "admin",
                "resourceId", "ORD-001",
                "relationshipType", "manages"
        )), "relationships");
        log.info("Seeded ReBAC relationship: admin → ORD-001 (manages)");
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> map(Object... kvPairs) {
        Map<String, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            m.put((String) kvPairs[i], (V) kvPairs[i + 1]);
        }
        return m;
    }
}