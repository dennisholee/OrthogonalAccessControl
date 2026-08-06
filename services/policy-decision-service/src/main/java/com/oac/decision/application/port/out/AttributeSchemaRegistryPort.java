package com.oac.decision.application.port.out;

import com.oac.decision.model.AttributeSchema;

import java.util.Optional;

/**
 * Output port for the Attribute Schema Registry (Section 4.7).
 * <p>
 * The registry is the declarative catalog of valid attribute names resolvable in
 * SpEL conditions ({@code subject.*}, {@code resource.*}, {@code environment.*},
 * {@code principalMemberships.*}). The PAP rejects policy submissions that reference
 * unknown attributes, and the PDP emits {@code DECISION_MISSING_ATTRIBUTE} when a
 * required attribute is absent at evaluation time.
 */
public interface AttributeSchemaRegistryPort {

    /**
     * Look up an attribute definition by its canonical dotted name
     * (e.g. {@code resource.dataSubjectCategory}).
     */
    Optional<AttributeSchema> find(String attributeName);

    /**
     * Whether an attribute reference is registered. Prefix lookups are allowed so a
     * map-typed attribute (e.g. {@code resource.suppressionFlags}) validates the
     * referenced map root.
     */
    boolean isRegistered(String attributeName);

    /** All registered attribute names (used for diagnostics). */
    java.util.List<String> allNames();
}
