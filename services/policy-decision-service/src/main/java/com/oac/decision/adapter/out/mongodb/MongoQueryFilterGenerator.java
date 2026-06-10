package com.oac.decision.adapter.out.mongodb;

import com.oac.decision.model.AttributeAccessLevel;
import com.oac.decision.model.AttributeAccessMap;
import com.oac.decision.model.BoundaryContext;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates MongoDB query filters from authorization decisions.
 * Translates attribute-level access maps into:
 * - $match stages for boundary and relationship filtering
 * - $project stages for field-level access control (masking, hiding, denying)
 * - $redact stages for document-level access control
 *
 * Designed for microservices to use directly when querying MongoDB
 * with pre-computed authorization decisions.
 */
public class MongoQueryFilterGenerator {

    /**
     * Build a $match filter that restricts results to authorized resource IDs.
     */
    public static Document buildResourceFilter(List<String> authorizedResourceIds) {
        if (authorizedResourceIds == null || authorizedResourceIds.isEmpty()) {
            return new Document("_id", new Document("$exists", false));
        }
        return new Document("_id", new Document("$in", authorizedResourceIds));
    }

    /**
     * Build a boundary-constrained $match filter for MongoDB queries.
     * Ensures documents only match if their boundary fields align with
     * the authorized boundary context.
     */
    public static Document buildBoundaryFilter(BoundaryContext boundary) {
        List<Document> filters = new ArrayList<>();
        if (boundary != null) {
            addBoundaryField(filters, "tenant", boundary.tenant());
            addBoundaryField(filters, "geography", boundary.geography());
            addBoundaryField(filters, "market", boundary.market());
            addBoundaryField(filters, "lineOfBusiness", boundary.lineOfBusiness());
            addBoundaryField(filters, "channel", boundary.channel());
        }
        if (filters.isEmpty()) {
            return new Document();
        }
        return new Document("$and", filters);
    }

    /**
     * Build a $project stage that applies field-level access constraints
     * from the authorization decision. Fields with MASK level are projected
     * as redacted. Fields with HIDDEN or DENY are excluded.
     *
     * @param accessMap the attribute-level access map from the decision response
     * @return a $project document that controls which fields are visible
     */
    public static Document buildFieldProjection(AttributeAccessMap accessMap) {
        Document projection = new Document();

        // Include _id by default
        projection.put("_id", 1);

        // Add field-level projections based on access levels
        if (accessMap != null) {
            for (var entry : accessMap.fieldAccess().entrySet()) {
                String field = entry.getKey();
                AttributeAccessLevel level = entry.getValue();
                switch (level) {
                    case READ, WRITE -> projection.put(field, 1);
                    case MASK -> projection.put(field, buildMaskProjection(field));
                    case HIDDEN, DENY -> projection.put(field, 0);
                }
            }
        }

        return projection;
    }

    /**
     * Build a $addFields stage with redacted values for masked fields.
     * This ensures masked fields return a sanitized value instead of being
     * completely excluded.
     */
    public static Document buildMaskStage(AttributeAccessMap accessMap) {
        Document addFields = new Document();

        if (accessMap != null) {
            for (String field : accessMap.getMaskedFields()) {
                // Replace masked field with a redacted value
                Document redactValue = new Document("$cond", List.of(
                        new Document("$eq", List.of("$" + field, null)),
                        null,
                        "*** REDACTED ***"
                ));
                addFields.put(field, redactValue);
            }
        }

        return addFields;
    }

    /**
     * Build a complete aggregation pipeline for MongoDB queries that combines
     * relationship-based resource filtering, boundary enforcement, and field-level
     * access control.
     */
    public static List<Document> buildAuthorizationPipeline(
            List<String> authorizedResourceIds,
            BoundaryContext boundary,
            AttributeAccessMap accessMap
    ) {
        List<Document> pipeline = new ArrayList<>();

        // Stage 1: Filter by authorized resource IDs
        if (authorizedResourceIds != null && !authorizedResourceIds.isEmpty()) {
            pipeline.add(new Document("$match", buildResourceFilter(authorizedResourceIds)));
        }

        // Stage 2: Apply boundary constraints
        Document boundaryFilter = buildBoundaryFilter(boundary);
        if (!boundaryFilter.isEmpty()) {
            pipeline.add(new Document("$match", boundaryFilter));
        }

        // Stage 3: Apply field-level masking
        if (accessMap != null && !accessMap.getMaskedFields().isEmpty()) {
            Document maskStage = buildMaskStage(accessMap);
            if (!maskStage.isEmpty()) {
                pipeline.add(new Document("$addFields", maskStage));
            }
        }

        // Stage 4: Apply field projection (hide/deny fields)
        if (accessMap != null && !accessMap.getHiddenFields().isEmpty()
                || !accessMap.getDeniedFields().isEmpty()) {
            pipeline.add(new Document("$project", buildFieldProjection(accessMap)));
        }

        return pipeline;
    }

    private static void addBoundaryField(List<Document> filters, String fieldName, String value) {
        if (value != null && !value.isEmpty() && !"*".equals(value)) {
            filters.add(new Document(fieldName, value));
        }
    }

    private static Document buildMaskProjection(String field) {
        // For masked fields, we keep the field in projection but replace the value
        Document cond = new Document("$cond", List.of(
                new Document("$eq", List.of("$" + field, null)),
                null,
                "*** REDACTED ***"
        ));
        return cond;
    }
}