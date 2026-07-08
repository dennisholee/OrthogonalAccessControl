package com.oac.enforcement;

import java.util.Map;

/**
 * Entitlement configuration extracted from
 * x-oac-entitlement vendor extensions in the OpenAPI contract.
 *
 * @param action           The action (e.g., "read", "approve") that this endpoint requires
 * @param resourceType     The type of resource being accessed (e.g., "order", "invoice")
 * @param resourceIdPath   Name of the path variable containing the resource ID (e.g., "orderId")
 * @param enforceFieldMask Whether field-level masking should be applied to the response
 * @param subjectType      Expected subject type for this endpoint (e.g., "user", "service")
 * @param subjectIdHeader  Override header to use for subject ID resolution
 */
public record OacEntitlementConfig(
        String action,
        String resourceType,
        String resourceIdPath,
        boolean enforceFieldMask,
        String subjectType,
        String subjectIdHeader
) {

    @SuppressWarnings("unchecked")
    public static OacEntitlementConfig from(Map<String, Object> yamlMap) {
        return new OacEntitlementConfig(
                str(yamlMap, "action"),
                str(yamlMap, "resourceType"),
                str(yamlMap, "resourceIdPath"),
                bool(yamlMap, "enforceFieldMask", false),
                str(yamlMap, "subjectType"),
                str(yamlMap, "subjectIdHeader")
        );
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        return v instanceof Boolean boolVal ? boolVal : def;
    }
}