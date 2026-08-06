package com.oac.emulator.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oac.decision.application.port.in.DecisionQueryUseCase;
import com.oac.decision.application.port.out.ConsistencyTokenStore;
import com.oac.decision.model.*;
import com.oac.emulator.InMemoryPolicyRegistryAdapter;
import com.oac.emulator.InMemoryRelationshipGraphAdapter;
import com.oac.decision.adapter.out.attribute.InMemoryAttributeResolverAdapter;
import com.oac.decision.adapter.out.audit.InMemoryAuditEvidenceAdapter;
import com.oac.decision.adapter.out.expression.SpelConditionEvaluatorAdapter;
import com.oac.decision.adapter.out.observability.MetricsObservabilityAdapter;
import com.oac.decision.adapter.out.policy.ClasspathFailOpenEndpointPolicyAdapter;
import com.oac.decision.adapter.out.schema.DefaultAttributeSchemaRegistryStub;
import com.oac.decision.application.service.DecisionApplicationService;
import com.oac.decision.application.service.decision.CircuitBreaker;
import com.oac.decision.application.service.decision.DecisionCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.io.File;
import java.util.*;

/**
 * OAC Policy Emulator CLI — standalone executable, no database required.
 *
 * <p>Runs the real PDP 9-rule decision chain against user-supplied
 * policies, relationships, and test cases from JSON files.</p>
 *
 * Usage:
 *   java -jar oac-policy-emulator-cli.jar --test policies.json relationships.json testcases.json
 *   java -jar oac-policy-emulator-cli.jar --single policies.json relationships.json <check-request-json>
 */
public class PolicyEmulatorCLI {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage:");
            System.out.println("  java -jar oac-policy-emulator-cli.jar --test policies.json relationships.json testcases.json");
            System.out.println("  java -jar oac-policy-emulator-cli.jar --single policies.json relationships.json <check-request-json>");
            System.exit(1);
        }

        String mode = args[0];
        File policiesFile = new File(args[1]);
        File relationshipsFile = new File(args[2]);

        // Load user-provided data
        List<Map<String, Object>> policies = MAPPER.readValue(policiesFile, new TypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> relationships = relationshipsFile.exists()
                ? MAPPER.readValue(relationshipsFile, new TypeReference<List<Map<String, Object>>>() {})
                : List.of();

        System.out.println("OAC Policy Emulator");
        System.out.println("  Loaded " + policies.size() + " policies");
        System.out.println("  Loaded " + relationships.size() + " relationships");
        System.out.println();

        // Wire the real PDP engine with in-memory adapters
        var engine = buildEngine(policies, relationships);

        switch (mode) {
            case "--test" -> {
                if (args.length < 4) {
                    System.out.println("Missing testcases.json argument");
                    System.exit(1);
                }
                runTestSuite(engine, new File(args[3]));
            }
            case "--single" -> runSingleCheck(engine, args.length > 3 ? args[3] : null);
            default -> {
                System.out.println("Unknown mode: " + mode);
                System.exit(1);
            }
        }
    }

    /**
     * Wires the real PDP 9-rule chain with in-memory adapters.
     * No Spring Boot, no MongoDB, no Docker — pure Java.
     */
    static DecisionQueryUseCase buildEngine(List<Map<String, Object>> policies,
                                             List<Map<String, Object>> relationships) {
        var policyRegistry = new InMemoryPolicyRegistryAdapter(policies);
        var relationshipGraph = new InMemoryRelationshipGraphAdapter(relationships);
        var attributeResolver = new InMemoryAttributeResolverAdapter();
        var auditEvidence = new InMemoryAuditEvidenceAdapter();
        var observability = new MetricsObservabilityAdapter(new SimpleMeterRegistry());
        var failOpenPolicy = new ClasspathFailOpenEndpointPolicyAdapter();
        var conditionEvaluator = new SpelConditionEvaluatorAdapter();
        var consistencyTokenStore = new ConsistencyTokenStore() {
            private String token;
            public java.util.Optional<String> getLatestToken(String scope) {
                return token != null ? java.util.Optional.of(token) : java.util.Optional.empty();
            }
            public String issueToken(String scope) {
                token = scope + "-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
                return token;
            }
        };
        // In-memory controller purpose registry — fail-open for non-CDP usage
        var controllerPurposeRegistry = new com.oac.decision.application.port.out.ControllerPurposeRegistryPort() {
            @Override
            public boolean isPurposeAuthorized(String tenant, String purpose) { return true; }
            @Override
            public void registerPurpose(String tenant, String purpose, String lawfulBasis) { }
        };

        return new DecisionApplicationService(
                policyRegistry, attributeResolver, auditEvidence, observability,
                failOpenPolicy, relationshipGraph, conditionEvaluator, consistencyTokenStore,
                new CircuitBreaker(), new DecisionCache(), controllerPurposeRegistry,
                new DefaultAttributeSchemaRegistryStub()
        );
    }

    /**
     * Run a batch of test cases and report pass/fail.
     */
    static void runTestSuite(DecisionQueryUseCase engine, File testCasesFile) throws Exception {
        if (!testCasesFile.exists()) {
            System.out.println("Test cases file not found: " + testCasesFile);
            System.exit(1);
        }

        List<Map<String, Object>> testCases = MAPPER.readValue(testCasesFile, new TypeReference<List<Map<String, Object>>>() {});
        System.out.println("Running " + testCases.size() + " test cases...\n");

        int passed = 0;
        int failed = 0;

        for (int i = 0; i < testCases.size(); i++) {
            Map<String, Object> tc = testCases.get(i);
            String name = str(tc, "name", "Test #" + (i + 1));

            @SuppressWarnings("unchecked")
            Map<String, Object> requestMap = (Map<String, Object>) tc.get("request");
            if (requestMap == null) {
                System.out.println("  ⚠️  " + name + " — missing 'request' field, skipping");
                continue;
            }

            CheckPermissionRequest request = buildRequest(requestMap);
            var response = engine.checkPermission(request);
            String expected = str(tc, "expected", "ALLOW");
            boolean ok = expected.equalsIgnoreCase(response.decision());

            if (ok) {
                System.out.println("  ✅ " + name + " → " + response.decision() + " (" + response.decisionCode() + ")");
                passed++;
            } else {
                System.out.println("  ❌ " + name + " → " + response.decision() + " (expected: " + expected + ")");
                System.out.println("     Decision code: " + response.decisionCode());
                System.out.println("     Matched policies: " + response.matchedPolicies());
                System.out.println("     Explanation: " + response.explanation());
                failed++;
            }
        }

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        System.exit(failed > 0 ? 1 : 0);
    }

    /**
     * Evaluate a single CheckPermission request from a JSON string or file.
     */
    static void runSingleCheck(DecisionQueryUseCase engine, String requestArg) throws Exception {
        Map<String, Object> requestMap;
        if (requestArg == null) {
            // Read from stdin
            requestMap = MAPPER.readValue(System.in, new TypeReference<Map<String, Object>>() {});
        } else if (requestArg.startsWith("{")) {
            // Inline JSON
            requestMap = MAPPER.readValue(requestArg, new TypeReference<Map<String, Object>>() {});
        } else {
            // File path
            requestMap = MAPPER.readValue(new File(requestArg), new TypeReference<Map<String, Object>>() {});
        }

        CheckPermissionRequest request = buildRequest(requestMap);
        var response = engine.checkPermission(request);

        // Print formatted output
        System.out.println("\n=== Decision ===");
        System.out.println("  Decision:       " + response.decision());
        System.out.println("  Code:           " + response.decisionCode());
        System.out.println("  Matched:        " + response.matchedPolicies());
        System.out.println("  Explanation:    " + response.explanation());
        System.out.println("  Field masks:    " + (response.attributeAccessMap() != null ? response.attributeAccessMap().fieldAccess() : "none"));
        System.out.println("  Evaluated at:   " + response.evaluatedAt());
    }

    /**
     * Convert a JSON map into a CheckPermissionRequest.
     */
    @SuppressWarnings("unchecked")
    static CheckPermissionRequest buildRequest(Map<String, Object> map) {
        // Subject
        Map<String, Object> subjectMap = (Map<String, Object>) map.getOrDefault("subject", Map.of());
        String subjectType = str(subjectMap, "type", "human");
        String subjectId = str(subjectMap, "id", "unknown");
        SubjectRef subject = new SubjectRef(subjectType, subjectId);

        // Action
        String action = str(map, "action", "read");

        // Resource
        Map<String, Object> resourceMap = (Map<String, Object>) map.getOrDefault("resource", Map.of());
        String resourceType = str(resourceMap, "type", "order");
        String resourceId = str(resourceMap, "id", "unknown");
        ResourceRef resource = new ResourceRef(resourceType, resourceId);

        // Boundary context
        BoundaryContext boundary = null;
        Map<String, Object> boundaryMap = (Map<String, Object>) map.get("boundaryContext");
        if (boundaryMap != null) {
            boundary = new BoundaryContext(
                    str(boundaryMap, "tenant", "default"),
                    str(boundaryMap, "geography", "global"),
                    str(boundaryMap, "market", "default"),
                    str(boundaryMap, "lineOfBusiness", "default"),
                    str(boundaryMap, "channel", "staff")
            );
        }

        // Runtime context (includes subject attributes like department, environment vars, etc.)
        Map<String, Object> runtimeContext = (Map<String, Object>) map.getOrDefault("runtimeContext", Map.of());

        // Consistency token
        String consistencyToken = str(map, "consistencyToken", null);

        return new CheckPermissionRequest(subject, action, resource, boundary, runtimeContext,
                consistencyToken, null, null, null, null);
    }

    private static String str(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultVal;
    }
}