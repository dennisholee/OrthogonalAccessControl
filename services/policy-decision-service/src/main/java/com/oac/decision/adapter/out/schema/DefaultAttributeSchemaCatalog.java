package com.oac.decision.adapter.out.schema;

import com.oac.decision.model.AttributeSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in Attribute Schema Registry catalog (Section 4.7). It covers the SpEL
 * root objects documented in Section 3.5 ({@code #subject}, {@code #resource},
 * {@code #environment}, {@code #principalMemberships}) plus the CDP workload
 * attributes the spec requires the registry to include: {@code dataSubjectCategory},
 * {@code suppressionFlags}, {@code consentAttributes}, {@code principalMemberships}.
 * <p>
 * Both the default stub and the MongoDB-backed adapter fall back to this catalog so
 * that policy evaluation keeps working without any registry seeding; test scenarios
 * may override individual entries (e.g. marking an attribute {@code isRequired}).
 */
final class DefaultAttributeSchemaCatalog {

    private DefaultAttributeSchemaCatalog() {
    }

    static Map<String, AttributeSchema> all() {
        Map<String, AttributeSchema> map = new LinkedHashMap<>();
        String jwt = "jwt", identity = "identity-directory", resource = "resource-metadata",
                consent = "consent-service", suppression = "suppression-service",
                membership = "membership-service", runtime = "runtime-context";

        // ---- subject (Section 3.5) ----
        add(map, "subject.id", AttributeSchema.TYPE_STRING, "SINGLE", jwt, "RESTRICTED", false);
        add(map, "subject.type", AttributeSchema.TYPE_STRING, "SINGLE", jwt, "PUBLIC", false);
        add(map, "subject.department", AttributeSchema.TYPE_STRING, "SINGLE", identity, "INTERNAL", false);
        add(map, "subject.market", AttributeSchema.TYPE_STRING, "SINGLE", identity, "INTERNAL", false);
        add(map, "subject.lob", AttributeSchema.TYPE_STRING, "SINGLE", identity, "INTERNAL", false);
        add(map, "subject.geography", AttributeSchema.TYPE_STRING, "SINGLE", identity, "INTERNAL", false);
        add(map, "subject.channel", AttributeSchema.TYPE_STRING, "SINGLE", identity, "INTERNAL", false);
        add(map, "subject.tenant", AttributeSchema.TYPE_STRING, "SINGLE", identity, "CONFIDENTIAL", false);
        add(map, "subject.clearance", AttributeSchema.TYPE_STRING, "SINGLE", identity, "RESTRICTED", false);

        // ---- resource (Section 3.5 + CDP attributes) ----
        add(map, "resource.id", AttributeSchema.TYPE_STRING, "SINGLE", resource, "RESTRICTED", false);
        add(map, "resource.type", AttributeSchema.TYPE_STRING, "SINGLE", resource, "PUBLIC", false);
        add(map, "resource.market", AttributeSchema.TYPE_STRING, "SINGLE", resource, "INTERNAL", false);
        add(map, "resource.lob", AttributeSchema.TYPE_STRING, "SINGLE", resource, "INTERNAL", false);
        add(map, "resource.geography", AttributeSchema.TYPE_STRING, "SINGLE", resource, "INTERNAL", false);
        add(map, "resource.channel", AttributeSchema.TYPE_STRING, "SINGLE", resource, "INTERNAL", false);
        add(map, "resource.tenant", AttributeSchema.TYPE_STRING, "SINGLE", resource, "CONFIDENTIAL", false);
        add(map, "resource.requesterId", AttributeSchema.TYPE_STRING, "SINGLE", resource, "CONFIDENTIAL", false);
        add(map, "resource.regulatoryRegime", AttributeSchema.TYPE_STRING, "SINGLE", resource, "CONFIDENTIAL", false);
        add(map, "resource.dataSources", AttributeSchema.TYPE_LIST, "MULTI", resource, "CONFIDENTIAL", false);
        add(map, "resource.consentVersion", AttributeSchema.TYPE_NUMBER, "SINGLE", consent, "CONFIDENTIAL", false);
        add(map, "resource.consentAttributes", AttributeSchema.TYPE_MAP, "MULTI", consent, "RESTRICTED", false);
        add(map, "resource.consentAgeDays", AttributeSchema.TYPE_NUMBER, "SINGLE", consent, "CONFIDENTIAL", false);
        add(map, "resource.dataSubjectCategory", AttributeSchema.TYPE_ENUM, "SINGLE", resource, "RESTRICTED", false,
                List.of("ADULT", "CHILD_UNDER_13", "CHILD_13_TO_16", "PARENTAL_PROXY"));
        add(map, "resource.suppressionFlags", AttributeSchema.TYPE_MAP, "MULTI", suppression, "RESTRICTED", false);
        add(map, "resource.segments", AttributeSchema.TYPE_LIST, "MULTI", resource, "CONFIDENTIAL", false);

        // ---- environment (Section 3.5 + runtime context) ----
        add(map, "environment.hour", AttributeSchema.TYPE_NUMBER, "SINGLE", runtime, "PUBLIC", false);
        add(map, "environment.currentHour", AttributeSchema.TYPE_NUMBER, "SINGLE", runtime, "PUBLIC", false);
        add(map, "environment.riskScore", AttributeSchema.TYPE_NUMBER, "SINGLE", runtime, "INTERNAL", false);
        add(map, "environment.purpose", AttributeSchema.TYPE_STRING, "SINGLE", runtime, "CONFIDENTIAL", false);
        add(map, "environment.exportDestination", AttributeSchema.TYPE_STRING, "SINGLE", runtime, "CONFIDENTIAL", false);
        add(map, "environment.deploymentEnvironment", AttributeSchema.TYPE_STRING, "SINGLE", runtime, "INTERNAL", false);

        // ---- principalMemberships (Section 4.33) ----
        add(map, "principalMemberships.tenants", AttributeSchema.TYPE_LIST, "MULTI", membership, "CONFIDENTIAL", false);
        add(map, "principalMemberships.markets", AttributeSchema.TYPE_LIST, "MULTI", membership, "CONFIDENTIAL", false);
        add(map, "principalMemberships.geographies", AttributeSchema.TYPE_LIST, "MULTI", membership, "CONFIDENTIAL", false);
        add(map, "principalMemberships.linesOfBusiness", AttributeSchema.TYPE_LIST, "MULTI", membership, "CONFIDENTIAL", false);
        add(map, "principalMemberships.channels", AttributeSchema.TYPE_LIST, "MULTI", membership, "CONFIDENTIAL", false);
        add(map, "principalMemberships.purposes", AttributeSchema.TYPE_LIST, "MULTI", membership, "CONFIDENTIAL", false);
        add(map, "principalMemberships.regulatoryRegimes", AttributeSchema.TYPE_LIST, "MULTI", membership, "CONFIDENTIAL", false);

        return map;
    }

    private static void add(Map<String, AttributeSchema> map, String name, String type,
                            String cardinality, String source, String sensitivity, boolean required) {
        add(map, name, type, cardinality, source, sensitivity, required, List.of());
    }

    private static void add(Map<String, AttributeSchema> map, String name, String type,
                            String cardinality, String source, String sensitivity, boolean required,
                            List<String> enumValues) {
        map.put(name, new AttributeSchema(name, type, cardinality, source, sensitivity, required, enumValues));
    }
}
