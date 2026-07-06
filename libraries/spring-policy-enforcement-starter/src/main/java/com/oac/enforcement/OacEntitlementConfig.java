package com.oac.enforcement;

import java.util.Map;

/**
 * Data class for entitlement configuration extracted from
 * x-oac-entitlement vendor extensions in the OpenAPI contract.
 */
public class OacEntitlementConfig {

    private final String action;
    private final String resourceType;
    private final String resourceIdPath;
    private final boolean enforceFieldMask;
    private final String subjectType;
    private final String subjectIdHeader;

    public OacEntitlementConfig(String action, String resourceType,
                                 String resourceIdPath, boolean enforceFieldMask,
                                 String subjectType, String subjectIdHeader) {
        this.action = action;
        this.resourceType = resourceType;
        this.resourceIdPath = resourceIdPath;
        this.enforceFieldMask = enforceFieldMask;
        this.subjectType = subjectType;
        this.subjectIdHeader = subjectIdHeader;
    }

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

    public String action() { return action; }
    public String resourceType() { return resourceType; }
    public String resourceIdPath() { return resourceIdPath; }
    public boolean enforceFieldMask() { return enforceFieldMask; }
    public String subjectType() { return subjectType; }
    public String subjectIdHeader() { return subjectIdHeader; }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }
}