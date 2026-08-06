package com.oac.sample.bdd;

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
        String resourceType = resourceId.contains("/")
                ? resourceId.substring(0, resourceId.indexOf("/"))
                : "order";

        String normalizedAction = action.toLowerCase();
        String subjectType = "read_aggregate".equals(normalizedAction) ? "workload" : "human";

        // Build runtime context and boundary from request headers
        Map<String, Object> runtimeContext = buildRuntimeContext();
        BoundaryContext boundary = buildBoundaryContext();

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
        return "ALLOW".equals(response.decision());
    }

    /**
     * Extended check that returns detailed PDP response (for field masks).
     */
    public Map<String, Object> checkPermissionWithDetails(
            String subjectId, String action, String resourceId, Map<String, Object> boundaryOverride) {

        String resourceType = resourceId.contains("/")
                ? resourceId.substring(0, resourceId.indexOf("/"))
                : "order";

        String normalizedAction = action.toLowerCase();
        String subjectType = "read_aggregate".equals(normalizedAction) ? "workload" : "human";

        BoundaryContext boundary = boundaryOverride != null
                ? new BoundaryContext(
                        str(boundaryOverride, "tenant", "acme-corp"),
                        str(boundaryOverride, "geography", "global"),
                        str(boundaryOverride, "market", "enterprise"),
                        str(boundaryOverride, "lineOfBusiness", "ecommerce"),
                        str(boundaryOverride, "channel", "staff"))
                : buildBoundaryContext();

        Map<String, Object> runtimeContext = buildRuntimeContext();

        // Inject field masks into runtime context for CSR subject with READ action
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

        Map<String, Object> flatAccessMap = new LinkedHashMap<>();
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

    /**
     * Build runtime context from incoming HTTP request headers.
     * Maps X-Department → subject.department, X-Current-Hour → currentHour,
     * X-Break-Glass-Active, X-Break-Glass-Reason, X-Requester-Id, etc.
     */
    private Map<String, Object> buildRuntimeContext() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return ctx;

        // Subject attributes — the SpEL context resolver maps the subject* key
        // prefix into subject attributes (subjectDepartment → department).
        String department = request.getHeader("X-Department");
        if (department != null && !department.isBlank()) {
            ctx.put("subjectDepartment", department);
        }

        // Environment attributes
        String currentHour = request.getHeader("X-Current-Hour");
        if (currentHour != null && !currentHour.isBlank()) {
            try {
                ctx.put("currentHour", Integer.parseInt(currentHour));
            } catch (NumberFormatException ignored) {}
        }

        // Break-glass
        String breakGlassActive = request.getHeader("X-Break-Glass-Active");
        if ("true".equalsIgnoreCase(breakGlassActive)) {
            ctx.put("breakGlassActive", true);
            String breakGlassReason = request.getHeader("X-Break-Glass-Reason");
            if (breakGlassReason != null) {
                ctx.put("breakGlassReason", breakGlassReason);
            }
        }

        // SoD
        String requesterId = request.getHeader("X-Requester-Id");
        if (requesterId != null && !requesterId.isBlank()) {
            ctx.put("requesterId", requesterId);
        }

        // Risk score
        String riskScore = request.getHeader("X-Risk-Score");
        if (riskScore != null && !riskScore.isBlank()) {
            try {
                ctx.put("riskScore", Integer.parseInt(riskScore));
            } catch (NumberFormatException ignored) {}
        }

        return ctx;
    }

    /**
     * Build boundary context from HTTP request headers.
     * Falls back to defaults if headers are absent.
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

    private String str(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val instanceof String s ? s : defaultValue;
    }
}