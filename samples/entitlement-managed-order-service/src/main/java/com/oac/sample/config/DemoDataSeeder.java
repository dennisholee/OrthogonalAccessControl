package com.oac.sample.config;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.oac.sample.model.Order;
import com.oac.sample.repository.OrderRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds demo data on startup when the {@code seed} profile is active.
 *
 * <p>Writes policies and relationships into the PDP's {@code oac_authorization}
 * database and orders into the sample service's {@code oac_sample} database,
 * matching the exact flat document format that {@code MongoPolicyRegistryAdapter}
 * and {@code MongoRelationshipGraphAdapter} query against.
 *
 * <p>Usage:
 * <pre>{@code
 *  mvn spring-boot:run -Dspring-boot.run.profiles=seed
 * }</pre>
 */
@Component
@Profile("seed")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final OrderRepository orderRepository;

    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/oac_sample}")
    private String orderDbUri;

    public DemoDataSeeder(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(String... args) {
        log.info("=== Seeding demo data ===");

        seedPdpPolicies();
        seedPdpRelationships();
        seedSampleOrders();

        log.info("=== Seeding complete ===");
    }

    // ----------------------------------------------------------------
    // PDP: oac_authorization.policies
    // Flat documents matching MongoPolicyRegistryAdapter.findMatchedPolicies()
    // ----------------------------------------------------------------
    private void seedPdpPolicies() {
        var policies = getPdpCollection("policies");
        policies.drop();

        // Policy 1: Explicit deny for attacker (Demo Scenario 2)
        policies.insertOne(new Document(map(
                "name", "POL.DENY.ATTACKER.v1",
                "effect", "DENY",
                "state", "ACTIVE",
                "subjectId", "attacker"
        )));
        log.info("Seeded DENY policy for attacker");

        // Policy 2: CSR can READ orders with PII masks (Demo Scenario 1)
        policies.insertOne(new Document(map(
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
        )));
        log.info("Seeded ALLOW policy for alice (CSR)");

        // Policy 3: Auditor can list orders without PII (Demo Scenario 3)
        policies.insertOne(new Document(map(
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
        )));
        log.info("Seeded ALLOW policy for auditor (PII fully redacted)");

        // Policy 4: Reporting service (workload) can read aggregates (Demo Scenario 4)
        policies.insertOne(new Document(map(
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
        )));
        log.info("Seeded WORKLOAD ALLOW policy for reporting-service");

        // Policy 5: ReBAC ALLOW for APPROVE (Demo Scenario 5)
        policies.insertOne(new Document(map(
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
        )));
        log.info("Seeded ReBAC ALLOW policy for approve");

        // Policy 6: Admin full access (Demo Scenario 6)
        policies.insertOne(new Document(map(
                "name", "POL.RBAC.ADMIN.FULL.ORDER.v1",
                "effect", "ALLOW",
                "state", "ACTIVE",
                "subjectId", "admin",
                "action", "*",
                "resourceType", "order",
                "tenant", "acme-corp",
                "channel", "staff"
        )));
        log.info("Seeded ALLOW policy for admin");
    }

    // ----------------------------------------------------------------
    // PDP: oac_authorization.relationships
    // ----------------------------------------------------------------
    private void seedPdpRelationships() {
        var relationships = getPdpCollection("relationships");
        relationships.drop();

        // For Demo Scenario 5: bob manages ORD-001 → allow approve
        // The README demo shows bob gets 403 (no relationship), so this is optional.
        // But to test the ReBAC approval flow successfully via curl:
        relationships.insertOne(new Document(map(
                "subjectId", "bob",
                "resourceId", "ORD-001",
                "relationshipType", "manages"
        )));
        log.info("Seeded ReBAC relationship: bob → ORD-001 (manages)");

        // Admin manages all orders (for ReBAC approve scenario with admin)
        relationships.insertOne(new Document(map(
                "subjectId", "admin",
                "resourceId", "ORD-001",
                "relationshipType", "manages"
        )));
        log.info("Seeded ReBAC relationship: admin → ORD-001 (manages)");
    }

    // ----------------------------------------------------------------
    // Order Service: oac_sample.orders
    // ----------------------------------------------------------------
    private void seedSampleOrders() {
        orderRepository.deleteAll();

        orderRepository.save(new Order("ORD-001", "Alice Johnson", "alice@acme.com",
                "123-45-6789", "Widget A", 10, 299.99, "alice"));
        orderRepository.save(new Order("ORD-002", "Bob Smith", "bob@acme.com",
                "987-65-4321", "Gadget B", 5, 149.95, "bob"));
        orderRepository.save(new Order("ORD-003", "Carol Davis", "carol@acme.com",
                "456-78-9012", "Service C", 1, 999.00, "carol"));
        orderRepository.save(new Order("ORD-004", "Dave Brown", "dave@acme.com",
                "321-54-9876", "Product D", 20, 49.99, "dave"));

        log.info("Seeded 4 sample orders");
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------
    private MongoCollection<Document> getPdpCollection(String collectionName) {
        // The PDP stores its data in the oac_authorization database
        String authUri = orderDbUri.replace("/oac_sample", "/oac_authorization");
        var mongoClient = MongoClients.create(authUri);
        MongoDatabase db = mongoClient.getDatabase("oac_authorization");
        return db.getCollection(collectionName);
    }

    /** Build a {@code LinkedHashMap} from alternating key-value pairs. */
    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> map(Object... kvPairs) {
        Map<String, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            m.put((String) kvPairs[i], (V) kvPairs[i + 1]);
        }
        return m;
    }
}
