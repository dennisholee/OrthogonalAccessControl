package com.oac.example.bdd;

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
        // Split resourceId like "order/ORD-001" into type and ID
        String resourceType;
        String resourceIdOnly;
        if (resourceId.contains("/")) {
            resourceType = resourceId.substring(0, resourceId.indexOf("/"));
            resourceIdOnly = resourceId.substring(resourceId.indexOf("/") + 1);
        } else {
            resourceType = "order";
            resourceIdOnly = resourceId;
        }

        // Normalize action to lowercase for PDP baseline rule compatibility
        String normalizedAction = action.toLowerCase();

        // Determine subject type: "workload" for read_aggregate action, "human" otherwise
        String subjectType = "read_aggregate".equals(normalizedAction) ? "workload" : "human";

        CheckPermissionRequest request = new CheckPermissionRequest(
                new SubjectRef(subjectType, subjectId),
                normalizedAction,
                new ResourceRef(resourceType, resourceIdOnly),
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
}