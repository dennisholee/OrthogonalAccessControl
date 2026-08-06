package com.oac.decision.adapter.out.schema;

import com.oac.decision.application.port.out.AttributeSchemaRegistryPort;
import com.oac.decision.model.AttributeSchema;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MongoDB-backed Attribute Schema Registry (Section 4.7) for the {@code mongodb}
 * profile. Reads the {@code attribute_schema} collection; falls back to the built-in
 * default catalog for any attribute not explicitly overridden, so policy evaluation
 * works without registry seeding. Explicit collection entries (e.g. a test marking an
 * attribute {@code isRequired}) take precedence.
 */
@Component
@Profile("mongodb")
public class MongoAttributeSchemaRegistryAdapter implements AttributeSchemaRegistryPort {

    private static final String COLLECTION = "attribute_schema";

    private final MongoTemplate mongoTemplate;
    private final Map<String, AttributeSchema> defaults = DefaultAttributeSchemaCatalog.all();

    public MongoAttributeSchemaRegistryAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<AttributeSchema> find(String attributeName) {
        AttributeSchema explicit = findExplicit(attributeName);
        if (explicit != null) {
            return Optional.of(explicit);
        }
        return Optional.ofNullable(defaults.get(attributeName));
    }

    @Override
    public boolean isRegistered(String attributeName) {
        if (findExplicit(attributeName) != null) {
            return true;
        }
        if (defaults.containsKey(attributeName)) {
            return true;
        }
        // Prefix lookup: allow map-root references such as resource.suppressionFlags
        int dot = attributeName.lastIndexOf('.');
        if (dot > 0) {
            String prefix = attributeName.substring(0, dot);
            return findExplicit(prefix) != null || defaults.containsKey(prefix);
        }
        return false;
    }

    @Override
    public List<String> allNames() {
        List<String> names = new ArrayList<>(defaults.keySet());
        try {
            List<Map> docs = mongoTemplate.find(Query.query(new Criteria()), Map.class, COLLECTION);
            for (Map doc : docs) {
                Object name = doc.get("attributeName");
                if (name != null && !names.contains(name.toString())) {
                    names.add(name.toString());
                }
            }
        } catch (Exception ignored) {
            // Registry collection optional
        }
        return names;
    }

    private AttributeSchema findExplicit(String attributeName) {
        try {
            Map doc = mongoTemplate.findOne(
                    Query.query(Criteria.where("attributeName").is(attributeName)),
                    Map.class, COLLECTION);
            if (doc == null) {
                return null;
            }
            Object type = doc.getOrDefault("attributeType", AttributeSchema.TYPE_STRING);
            Object cardinality = doc.getOrDefault("cardinality", AttributeSchema.CARDINALITY_SINGLE);
            Object source = doc.getOrDefault("source", "resource-metadata");
            Object sensitivity = doc.getOrDefault("sensitivity", AttributeSchema.SENSITIVITY_INTERNAL);
            boolean required = Boolean.TRUE.equals(doc.get("isRequired"));
            List<String> enumValues = new ArrayList<>();
            if (doc.get("enumValues") instanceof List<?> evs) {
                for (Object ev : evs) {
                    if (ev != null) enumValues.add(ev.toString());
                }
            }
            return new AttributeSchema(
                    attributeName,
                    type == null ? AttributeSchema.TYPE_STRING : type.toString(),
                    cardinality == null ? AttributeSchema.CARDINALITY_SINGLE : cardinality.toString(),
                    source == null ? "resource-metadata" : source.toString(),
                    sensitivity == null ? AttributeSchema.SENSITIVITY_INTERNAL : sensitivity.toString(),
                    required,
                    enumValues
            );
        } catch (Exception e) {
            return null;
        }
    }
}
