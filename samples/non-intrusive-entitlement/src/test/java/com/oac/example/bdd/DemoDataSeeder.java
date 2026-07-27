package com.oac.example.bdd;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds demo data into Testcontainers MongoDB matching the exact flat document
 * format that MongoPolicyRegistryAdapter and MongoRelationshipGraphAdapter query against.
 *
 * <p>Includes both standard policies (deny, CSR, auditor, admin, workload, ReBAC)
 * and extended policies for ABAC, caveat, break-glass, boundary, and hierarchical ReBAC scenarios.
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
    // ----------------------------------------------------------------
    private void seedPdpPolicies() {
        try {
            mongoTemplate.dropCollection("policies");
        } catch (Exception e) { }

        // Policy 1: Explicit deny for attacker
        mongoTemplate.save(new Document(map(
                "name", "POL.DENY.ATTACKER.v1",
                "effect", "DENY",
                "state", "ACTIVE",
                "subjectId", "attacker"
        )), "policies");
        log.info("Seeded DENY policy for attacker");

        // Policy 2: CSR - alice can READ orders with PII masks
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

        // === EXTENDED POLICIES ===

        // Policy 7: SpEL — CSR ("csr-user") department-based access (compliance)
        mongoTemplate.save(new Document(map(
                "name", "POL.SPEL.CSR.DEPARTMENT.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "subjectId", "csr-user",
                "action", "read",
                "resourceType", "order",
                "tenant", "acme-corp",
                "geography", "global",
                "market", "enterprise",
                "lineOfBusiness", "ecommerce",
                "channel", "staff",
                "spelCondition", "subject.department == 'compliance'"
        )), "policies");
        log.info("Seeded SpEL policy for csr-user (department=compliance)");

        // Policy 8: Time-window caveat for csr-user (business hours only)
        mongoTemplate.save(new Document(map(
                "name", "POL.TIME.WINDOW.CSR.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "subjectId", "csr-user",
                "action", "read",
                "resourceType", "order",
                "tenant", "acme-corp",
                "geography", "global",
                "market", "enterprise",
                "lineOfBusiness", "ecommerce",
                "channel", "staff",
                "timeWindow", "09:00-17:00 UTC"
        )), "policies");
        log.info("Seeded time-window policy for csr-user");

        // Policy 9: Break-glass for emergency access
        mongoTemplate.save(new Document(map(
                "name", "POL.BREAK.GLASS.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "action", "read",
                "resourceType", "order",
                "tenant", "acme-corp",
                "geography", "global",
                "market", "enterprise",
                "lineOfBusiness", "ecommerce",
                "channel", "staff"
        )), "policies");
        log.info("Seeded break-glass ALLOW policy for read order");

        // Policy 10: Tenant-scoped for csr-user (only tenant-a)
        mongoTemplate.save(new Document(map(
                "name", "POL.TENANT.CSR.TENANT_A.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "subjectId", "csr-user",
                "action", "read",
                "resourceType", "order",
                "tenant", "tenant-a",
                "geography", "global",
                "market", "enterprise",
                "lineOfBusiness", "ecommerce",
                "channel", "staff"
        )), "policies");
        log.info("Seeded tenant-scoped policy for csr-user (tenant-a)");

        // Policy 11: ReBAC ALLOW for READ (hierarchical)
        mongoTemplate.save(new Document(map(
                "name", "POL.REBAC.READ.ORDER.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "action", "read",
                "resourceType", "order",
                "requiredRelationship", "manages",
                "tenant", "acme-corp",
                "geography", "global",
                "market", "enterprise",
                "lineOfBusiness", "ecommerce",
                "channel", "staff"
        )), "policies");
        log.info("Seeded ReBAC ALLOW policy for read");

        // Policy 12: SoD — self-approval prevention
        mongoTemplate.save(new Document(map(
                "name", "POL.SPEL.SOD.APPROVE.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "action", "approve",
                "resourceType", "order",
                "tenant", "acme-corp",
                "geography", "global",
                "market", "enterprise",
                "lineOfBusiness", "ecommerce",
                "channel", "staff",
                "spelCondition", "subject.id != resource.requester_id"
        )), "policies");
        log.info("Seeded SoD SpEL policy for approve (subject.id != requester)");
    }

    // ----------------------------------------------------------------
    // PDP: relationships collection
    // ----------------------------------------------------------------
    private void seedPdpRelationships() {
        try {
            mongoTemplate.dropCollection("relationships");
        } catch (Exception e) { }

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

        // Hierarchical ReBAC: CEO → VP → Director → CSR → ORD-001
        // This enables CEO to read ORD-001 via 3-hop manages chain
        mongoTemplate.save(new Document(map(
                "subjectId", "CEO",
                "resourceId", "VP",
                "relationshipType", "manages"
        )), "relationships");
        mongoTemplate.save(new Document(map(
                "subjectId", "VP",
                "resourceId", "Director",
                "relationshipType", "manages"
        )), "relationships");
        mongoTemplate.save(new Document(map(
                "subjectId", "Director",
                "resourceId", "CSR",
                "relationshipType", "manages"
        )), "relationships");
        mongoTemplate.save(new Document(map(
                "subjectId", "CSR",
                "resourceId", "ORD-001",
                "relationshipType", "manages"
        )), "relationships");
        log.info("Seeded hierarchical ReBAC chain: CEO→VP→Director→CSR→ORD-001");

        // alice has "approver" relationship to ORD-001 (not "manages")
        // Used for ReBAC type-mismatch scenario
        mongoTemplate.save(new Document(map(
                "subjectId", "alice",
                "resourceId", "ORD-001",
                "relationshipType", "approver"
        )), "relationships");
        log.info("Seeded ReBAC relationship: alice → ORD-001 (approver)");
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