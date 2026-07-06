package com.oac.enforcement;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring HandlerInterceptor that enforces entitlements without modifying
 * generated code.
 *
 * 1. Resolves the operationId from the request path + method using compiled patterns
 * 2. Looks up the entitlement config from the registry
 * 3. Extracts the subjectId from configurable headers
 * 4. Extracts the resourceId from path variables using the path template
 * 5. Calls the PDP to check permission
 * 6. Denies (403) if PDP returns DENY
 */
public class OacEnforcementInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OacEnforcementInterceptor.class);

    private final EntitlementRegistry registry;
    private final DecisionClient decisionClient;
    private final OacEntitlementProperties properties;

    public OacEnforcementInterceptor(EntitlementRegistry registry,
                                      DecisionClient decisionClient,
                                      OacEntitlementProperties properties) {
        this.registry = registry;
        this.decisionClient = decisionClient;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        String path = request.getRequestURI();
        // Strip context path if present
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            path = path.substring(contextPath.length());
        }

        String method = request.getMethod();
        String operationId = registry.resolveOperationId(method, path);

        if (operationId == null) {
            return true; // No matching route — allow through
        }

        OacEntitlementConfig config = registry.get(operationId);
        if (config == null) {
            return true; // Not protected — allow through
        }

        String subjectId = resolveSubjectId(request, config);
        if (subjectId == null || subjectId.isBlank()) {
            send403(response, "Missing identity header for protected endpoint");
            return false;
        }

        String resourceId = resolveResourceId(path, config, operationId);
        String fullResourceId = config.resourceType() + "/" + (resourceId != null ? resourceId : "*");

        boolean allowed = decisionClient.checkPermission(subjectId, config.action(), fullResourceId);

        if (!allowed) {
            log.warn("OAC DENIED: {} {} {}", subjectId, config.action(), fullResourceId);
            send403(response, "Access denied: " + subjectId + " cannot " + config.action() + " " + fullResourceId);
            return false;
        }

        log.debug("OAC ALLOWED: {} {} {}", subjectId, config.action(), fullResourceId);
        return true;
    }

    private String resolveSubjectId(HttpServletRequest request, OacEntitlementConfig config) {
        // Per-operation override takes precedence
        if (config.subjectIdHeader() != null) {
            return request.getHeader(config.subjectIdHeader());
        }
        // Default: check user header, then service header
        String userId = request.getHeader(properties.getIdentity().getUserHeader());
        if (userId != null) return userId;
        return request.getHeader(properties.getIdentity().getServiceHeader());
    }

    /**
     * Resolves the resource ID from the request path using the route mapping's
     * path template. For example, given pathTemplate="/api/orders/{orderId}/approve"
     * and actualPath="/api/orders/ORD-001/approve", extracts "ORD-001".
     *
     * Falls back to the last path segment if no resourceIdPath is configured.
     */
    private String resolveResourceId(String path, OacEntitlementConfig config, String operationId) {
        if (config.resourceIdPath() == null) return null;

        // Try to find the matching route mapping and extract the resource ID
        // by matching the path template against the actual path
        EntitlementRegistry.RouteMapping mapping = registry.getRouteMapping(operationId);
        if (mapping != null) {
            String resolved = extractPathVariable(mapping.pathTemplate(), path, config.resourceIdPath());
            if (resolved != null) return resolved;
        }

        // Fallback: extract the last path segment
        Pattern pattern = Pattern.compile("/([^/]+)$");
        Matcher matcher = pattern.matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Extracts the value of a named path variable from the actual path
     * using the path template. For example:
     *   template="/api/orders/{orderId}/approve", path="/api/orders/ORD-001/approve", var="orderId"
     *   returns "ORD-001"
     */
    private static String extractPathVariable(String template, String actualPath, String varName) {
        // Split template and actual path into segments
        String[] templateSegments = template.split("/");
        String[] actualSegments = actualPath.split("/");

        if (templateSegments.length != actualSegments.length) return null;

        for (int i = 0; i < templateSegments.length; i++) {
            String ts = templateSegments[i];
            if (ts.equals("{" + varName + "}")) {
                return actualSegments[i];
            }
        }
        return null;
    }

    private void send403(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\",\"code\":\"ACCESS_DENIED\"}");
    }
}