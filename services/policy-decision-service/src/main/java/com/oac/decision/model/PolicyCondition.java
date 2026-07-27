package com.oac.decision.model;

import java.util.Map;

/**
 * A typed, composable condition that can be attached to a policy.
 * Each condition represents a single authorization constraint (e.g., a SpEL
 * expression, a time window caveat, a ReBAC relationship requirement, a source
 * IP range restriction, or a field-level access mask).
 * <p>
 * Conditions in a policy's {@code conditions[]} array are AND-ed together:
 * ALL conditions must pass for the policy to apply.
 * <p>
 * This replaces the previous ad-hoc flat JSON field approach
 * (e.g., {@code spelCondition}, {@code timeWindow}, {@code sourceIpRange},
 * {@code requiredRelationship}, {@code fieldMasks}) with a unified,
 * self-documenting, type-safe model.
 */
public record PolicyCondition(
        String type,
        Map<String, Object> params
) {
    // Standard condition type constants
    public static final String TYPE_SPEL = "spel";
    public static final String TYPE_TIME_WINDOW = "timeWindow";
    public static final String TYPE_SOURCE_IP = "sourceIp";
    public static final String TYPE_REBAC = "rebac";
    public static final String TYPE_FIELD_MASK = "fieldMask";
    public static final String TYPE_BREAK_GLASS = "breakGlass";

    // Common parameter key constants
    public static final String PARAM_EXPRESSION = "expression";
    public static final String PARAM_WINDOW = "window";
    public static final String PARAM_TIMEZONE = "timezone";
    public static final String PARAM_CIDR = "cidr";
    public static final String PARAM_RELATIONSHIP_TYPE = "relationshipType";
    public static final String PARAM_MAX_DEPTH = "maxDepth";
    public static final String PARAM_FIELDS = "fields";
    public static final String PARAM_TAGS = "tags";

    // Factory methods for creating conditions

    public static PolicyCondition spel(String expression) {
        return new PolicyCondition(TYPE_SPEL, Map.of(PARAM_EXPRESSION, expression));
    }

    public static PolicyCondition timeWindow(String window) {
        return new PolicyCondition(TYPE_TIME_WINDOW, Map.of(PARAM_WINDOW, window));
    }

    public static PolicyCondition timeWindow(String window, String timezone) {
        return new PolicyCondition(TYPE_TIME_WINDOW, Map.of(PARAM_WINDOW, window, PARAM_TIMEZONE, timezone));
    }

    public static PolicyCondition sourceIp(String cidr) {
        return new PolicyCondition(TYPE_SOURCE_IP, Map.of(PARAM_CIDR, cidr));
    }

    public static PolicyCondition rebac(String relationshipType) {
        return new PolicyCondition(TYPE_REBAC, Map.of(PARAM_RELATIONSHIP_TYPE, relationshipType,
                PARAM_MAX_DEPTH, 3));
    }

    public static PolicyCondition rebac(String relationshipType, int maxDepth) {
        return new PolicyCondition(TYPE_REBAC, Map.of(PARAM_RELATIONSHIP_TYPE, relationshipType,
                PARAM_MAX_DEPTH, maxDepth));
    }

    public static PolicyCondition fieldMask(Map<String, String> fieldLevels) {
        return new PolicyCondition(TYPE_FIELD_MASK, Map.of(PARAM_FIELDS, fieldLevels));
    }

    public static PolicyCondition fieldMaskWithTags(Map<String, String> fields, Map<String, String> tags) {
        return new PolicyCondition(TYPE_FIELD_MASK, Map.of(PARAM_FIELDS, fields, PARAM_TAGS, tags));
    }

    public static PolicyCondition breakGlass() {
        return new PolicyCondition(TYPE_BREAK_GLASS, Map.of());
    }
}