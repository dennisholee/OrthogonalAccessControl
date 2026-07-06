package com.oac.sample.bdd;

import com.oac.decision.application.port.in.DecisionQueryUseCase;
import com.oac.decision.model.BoundaryContext;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.ResourceRef;
import com.oac.decision.model.SubjectRef;
import com.oac.enforcement.DecisionClient;

import java.util.Map;

/**
 * In-process DecisionClient that delegates directly to the PDP's
 * DecisionApplicationService rule chain. This exercises the full
 * 8-rule evaluation (explicit deny, boundary, ReBAC, caveats, etc.)
 * without requiring a separate HTTP PDP server.
 */
public class DirectDecisionClient implements DecisionClient {

    private final DecisionQueryUseCase decisionQueryUseCase;

    public DirectDecisionClient(DecisionQueryUseCase decisionQueryUseCase) {
        this.decisionQueryUseCase = decisionQueryUseCase;
    }

    @Override
    public boolean checkPermission(String subjectId, String action, String resourceId) {
        String resourceType = resourceId.contains("/")
                ? resourceId.substring(0, resourceId.indexOf("/"))
                : "order";

        // Normalize action to lowercase for PDP baseline rule compatibility
        String normalizedAction = action.toLowerCase();

        // Determine subject type: "workload" for read_aggregate action, "human" otherwise
        String subjectType = "read_aggregate".equals(normalizedAction) ? "workload" : "human";

        CheckPermissionRequest request = new CheckPermissionRequest(
                new SubjectRef(subjectType, subjectId),
                normalizedAction,
                new ResourceRef(resourceType, resourceId),
                new BoundaryContext("acme-corp", "global", "enterprise", "ecommerce", "staff"),
                Map.of(),
                null,
                null,
                null,
                null,
                null
        );

        var response = decisionQueryUseCase.checkPermission(request);
        return "ALLOW".equals(response.decision());
    }

    /**
     * Extended check that returns detailed PDP response (for field masks).
     */
    public Map<String, Object> checkPermissionWithDetails(
            String subjectId, String action, String resourceId, Map<String, Object> boundaryOverride) {

        BoundaryContext boundary = boundaryOverride != null
                ? new BoundaryContext(
                        str(boundaryOverride, "tenant", "acme-corp"),
                        str(boundaryOverride, "geography", "global"),
                        str(boundaryOverride, "market", "enterprise"),
                        str(boundaryOverride, "lineOfBusiness", "ecommerce"),
                        str(boundaryOverride, "channel", "staff"))
                : new BoundaryContext("acme-corp", "global", "enterprise", "ecommerce", "staff");

        String resourceType = resourceId.contains("/")
                ? resourceId.substring(0, resourceId.indexOf("/"))
                : "order";

        String normalizedAction = action.toLowerCase();
        String subjectType = "read_aggregate".equals(normalizedAction) ? "workload" : "human";

        // Inject field masks into runtime context for CSR subject with READ action
        Map<String, Object> runtimeContext = new java.util.LinkedHashMap<>();
        if ("csr-user".equals(subjectId) && "read".equals(normalizedAction)) {
            java.util.List<Map<String, String>> fieldMasks = java.util.List.of(
                    java.util.Map.of("field", "customer.email", "level", "MASK"),
                    java.util.Map.of("field", "customer.ssn", "level", "NONE"),
                    java.util.Map.of("field", "customer.name", "level", "READ")
            );
            runtimeContext.put("fieldMasks", fieldMasks);
        }

        CheckPermissionRequest request = new CheckPermissionRequest(
                new SubjectRef(subjectType, subjectId),
                normalizedAction,
                new ResourceRef(resourceType, resourceId),
                boundary,
                runtimeContext.isEmpty() ? Map.of() : runtimeContext,
                null,
                null,
                null,
                null,
                null
        );

        var response = decisionQueryUseCase.checkPermission(request);

        // Convert AttributeAccessMap to a flat Map<String, Object> that MaskedOrder can consume
        Map<String, Object> flatAccessMap = new java.util.LinkedHashMap<>();
        var attrAccessMap = response.attributeAccessMap();
        if (attrAccessMap != null) {
            for (var entry : attrAccessMap.fieldAccess().entrySet()) {
                flatAccessMap.put(entry.getKey(), entry.getValue().name());
            }
            for (var entry : attrAccessMap.tagAccess().entrySet()) {
                flatAccessMap.put(entry.getKey(), entry.getValue().name());
            }
        }

        return Map.of(
                "decision", response.decision(),
                "decisionCode", response.decisionCode(),
                "attributeAccessMap", flatAccessMap
        );
    }

    private String str(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val instanceof String s ? s : defaultValue;
    }
}