package com.oac.example.bdd;

import com.oac.decision.application.port.in.DecisionQueryUseCase;
import com.oac.decision.model.BoundaryContext;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.ResourceRef;
import com.oac.decision.model.SubjectRef;
import com.oac.enforcement.DecisionClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-process DecisionClient that delegates directly to the PDP's
 * DecisionApplicationService rule chain. Supports runtime context
 * injection from HTTP headers for ABAC, caveat, and boundary scenarios.
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

        // Build runtime context and boundary from request headers
        Map<String, Object> runtimeContext = buildRuntimeContext();
        BoundaryContext boundary = buildBoundaryContext();

        CheckPermissionRequest request = new CheckPermissionRequest(
                new SubjectRef(subjectType, subjectId),
                normalizedAction,
                new ResourceRef(resourceType, resourceIdOnly),
                boundary,
                runtimeContext.isEmpty() ? Map.of() : runtimeContext,
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
     * Build runtime context from incoming HTTP request headers.
     */
    private Map<String, Object> buildRuntimeContext() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return ctx;

        String department = request.getHeader("X-Department");
        if (department != null && !department.isBlank()) {
            ctx.put("department", department);
        }

        String currentHour = request.getHeader("X-Current-Hour");
        if (currentHour != null && !currentHour.isBlank()) {
            try {
                ctx.put("currentHour", Integer.parseInt(currentHour));
            } catch (NumberFormatException ignored) {}
        }

        String breakGlassActive = request.getHeader("X-Break-Glass-Active");
        if ("true".equalsIgnoreCase(breakGlassActive)) {
            ctx.put("breakGlassActive", true);
            String breakGlassReason = request.getHeader("X-Break-Glass-Reason");
            if (breakGlassReason != null) {
                ctx.put("breakGlassReason", breakGlassReason);
            }
        }

        String requesterId = request.getHeader("X-Requester-Id");
        if (requesterId != null && !requesterId.isBlank()) {
            ctx.put("requesterId", requesterId);
        }

        return ctx;
    }

    /**
     * Build boundary context from HTTP request headers. Falls back to defaults.
     */
    private BoundaryContext buildBoundaryContext() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return new BoundaryContext("acme-corp", "global", "enterprise", "ecommerce", "staff");
        }

        String tenant = request.getHeader("X-Tenant");
        String geography = request.getHeader("X-Geography");
        String market = request.getHeader("X-Market");
        String lob = request.getHeader("X-Line-Of-Business");
        String channel = request.getHeader("X-Channel");

        return new BoundaryContext(
                tenant != null ? tenant : "acme-corp",
                geography != null ? geography : "global",
                market != null ? market : "enterprise",
                lob != null ? lob : "ecommerce",
                channel != null ? channel : "staff"
        );
    }

    private static HttpServletRequest getCurrentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }
}