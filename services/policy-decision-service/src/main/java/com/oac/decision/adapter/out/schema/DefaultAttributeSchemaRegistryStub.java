package com.oac.decision.adapter.out.schema;

import com.oac.decision.application.port.out.AttributeSchemaRegistryPort;
import com.oac.decision.model.AttributeSchema;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default in-memory Attribute Schema Registry for non-MongoDB profiles.
 * <p>
 * Serves the built-in catalog from {@link DefaultAttributeSchemaCatalog}. Individual
 * entries may be overridden at runtime (e.g. tests marking an attribute required)
 * via {@link #register(AttributeSchema)}.
 */
@Component
@Profile("!mongodb")
public class DefaultAttributeSchemaRegistryStub implements AttributeSchemaRegistryPort {

    private final Map<String, AttributeSchema> attributes = DefaultAttributeSchemaCatalog.all();

    /** Register or override a schema entry (used by tests and PAP bootstrap). */
    public void register(AttributeSchema schema) {
        attributes.put(schema.attributeName(), schema);
    }

    @Override
    public Optional<AttributeSchema> find(String attributeName) {
        return Optional.ofNullable(attributes.get(attributeName));
    }

    @Override
    public boolean isRegistered(String attributeName) {
        if (attributes.containsKey(attributeName)) {
            return true;
        }
        // Prefix lookup: allow map-root references such as resource.suppressionFlags
        // where the referenced path is a parent of a registered attribute.
        int dot = attributeName.lastIndexOf('.');
        return dot > 0 && attributes.containsKey(attributeName.substring(0, dot));
    }

    @Override
    public List<String> allNames() {
        return List.copyOf(attributes.keySet());
    }
}
