package com.oac.enforcement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Parses OpenAPI YAML contracts at startup and extracts
 * x-oac-entitlement vendor extensions into a runtime config map.
 *
 * Supports multiple contracts and builds URL pattern → operationId mappings
 * for O(1) lookup during HTTP interceptor calls.
 */
public class EntitlementRegistry {

    private static final Logger log = LoggerFactory.getLogger(EntitlementRegistry.class);

    private final List<String> contractPaths;
    private final ResourceLoader resourceLoader;
    private final boolean failClosed;

    /** operationId → entitlement config */
    private final Map<String, OacEntitlementConfig> registry = new LinkedHashMap<>();
    /** Compiled regex pattern → operationId for URL matching */
    private final List<RouteMapping> routeMappings = new ArrayList<>();

    public EntitlementRegistry(List<String> contractPaths, ResourceLoader resourceLoader, boolean failClosed) {
        this.contractPaths = contractPaths;
        this.resourceLoader = resourceLoader;
        this.failClosed = failClosed;
    }

    @PostConstruct
    void parseContracts() {
        for (String contractPath : contractPaths) {
            parseContract(contractPath);
        }
        // Sort routes so literal paths (no path variables) come first.
        // This prevents /api/orders/{orderId} from matching /api/orders/aggregate.
        routeMappings.sort((a, b) -> {
            boolean aHasVar = a.pathTemplate().contains("{");
            boolean bHasVar = b.pathTemplate().contains("{");
            if (aHasVar && !bHasVar) return 1;
            if (!aHasVar && bHasVar) return -1;
            return 0;
        });
        log.info("OAC ClientKit loaded {} entitlement configs across {} routes from {} contracts",
                registry.size(), routeMappings.size(), contractPaths.size());

        if (failClosed && registry.isEmpty() && !contractPaths.isEmpty()) {
            throw new IllegalStateException(
                    "OAC ClientKit fail-closed: no entitlements loaded from contracts: " + contractPaths);
        }
    }

    private void parseContract(String contractPath) {
        try {
            Resource resource = resourceLoader.getResource(contractPath);
            if (!resource.exists()) {
                if (failClosed) throw new IllegalStateException("Contract not found: " + contractPath);
                log.warn("OAC ClientKit: contract not found: {}", contractPath);
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                Yaml yaml = new Yaml();
                Map<String, Object> doc = yaml.load(is);
                Map<String, Object> paths = (Map<String, Object>) doc.get("paths");
                if (paths == null) return;

                for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                    String pathTemplate = pathEntry.getKey();
                    Map<String, Object> methods = (Map<String, Object>) pathEntry.getValue();
                    if (methods == null) continue;

                    for (Map.Entry<String, Object> methodEntry : methods.entrySet()) {
                        String httpMethod = methodEntry.getKey().toLowerCase();
                        if ("parameters".equals(httpMethod)) continue; // path-level params

                        Map<String, Object> operation = (Map<String, Object>) methodEntry.getValue();
                        if (operation == null) continue;

                        String operationId = (String) operation.get("operationId");
                        Map<String, Object> entitlement = (Map<String, Object>) operation.get("x-oac-entitlement");

                        if (operationId != null && entitlement != null) {
                            OacEntitlementConfig config = OacEntitlementConfig.from(entitlement);
                            registry.put(operationId, config);
                            routeMappings.add(new RouteMapping(pathTemplate, httpMethod, operationId));
                            log.debug("Registered: {} {} → opId={}, action={}",
                                    httpMethod.toUpperCase(), pathTemplate, operationId, config.action());
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (failClosed) throw new RuntimeException("Failed to parse contract: " + contractPath, e);
            log.error("OAC ClientKit: failed to parse contract: {}", contractPath, e);
        }
    }

    /**
     * Find the operationId matching the given HTTP method and path.
     * Uses compiled regex patterns from the OpenAPI path templates.
     */
    public String resolveOperationId(String httpMethod, String actualPath) {
        for (RouteMapping mapping : routeMappings) {
            if (mapping.httpMethod().equalsIgnoreCase(httpMethod)
                    && mapping.pattern().matcher(actualPath).matches()) {
                return mapping.operationId();
            }
        }
        return null;
    }

    public OacEntitlementConfig get(String operationId) {
        return registry.get(operationId);
    }

    public RouteMapping getRouteMapping(String operationId) {
        for (RouteMapping mapping : routeMappings) {
            if (mapping.operationId().equals(operationId)) {
                return mapping;
            }
        }
        return null;
    }

    public boolean isProtected(String operationId) {
        return registry.containsKey(operationId);
    }

    public int size() {
        return registry.size();
    }

    /**
     * Converts an OpenAPI path template (e.g., /orders/{orderId}) to a compiled regex.
     */
    public record RouteMapping(String pathTemplate, String httpMethod, String operationId, Pattern pattern) {
        public RouteMapping(String pathTemplate, String httpMethod, String operationId) {
            this(pathTemplate, httpMethod, operationId, compilePathTemplate(pathTemplate));
        }

        private static Pattern compilePathTemplate(String template) {
            // Convert /orders/{orderId}/approve → /orders/([^/]+)/approve
            String regex = template.replaceAll("\\{[^}]+\\}", "([^/]+)");
            return Pattern.compile("^" + regex + "$");
        }
    }
}