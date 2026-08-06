package com.oac.decision.model;

import java.util.List;

/**
 * A registered attribute definition in the Attribute Schema Registry (Section 4.7).
 * <p>
 * SpEL conditions in policy documents MUST reference only registered attributes.
 * References to unknown attributes are rejected at submission time; attributes
 * declared {@code isRequired} that are missing at evaluation time trigger
 * {@code DECISION_MISSING_ATTRIBUTE}.
 */
public record AttributeSchema(
        String attributeName,
        String attributeType,
        String cardinality,
        String source,
        String sensitivity,
        boolean isRequired,
        List<String> enumValues
) {

    public static final String TYPE_STRING = "STRING";
    public static final String TYPE_NUMBER = "NUMBER";
    public static final String TYPE_BOOLEAN = "BOOLEAN";
    public static final String TYPE_DATETIME = "DATETIME";
    public static final String TYPE_ENUM = "ENUM";
    public static final String TYPE_LIST = "LIST";
    public static final String TYPE_MAP = "MAP";

    public static final String CARDINALITY_SINGLE = "SINGLE";
    public static final String CARDINALITY_MULTI = "MULTI";

    public static final String SENSITIVITY_PUBLIC = "PUBLIC";
    public static final String SENSITIVITY_INTERNAL = "INTERNAL";
    public static final String SENSITIVITY_CONFIDENTIAL = "CONFIDENTIAL";
    public static final String SENSITIVITY_RESTRICTED = "RESTRICTED";

    public AttributeSchema {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }
}
