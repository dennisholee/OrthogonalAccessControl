package com.oac.decision.adapter.out.purpose;

import com.oac.decision.application.port.out.ControllerPurposeRegistryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MongoDB-backed controller purpose registry (Section 4.17).
 * <p>
 * Reads from the {@code controller_purpose_registry} collection:
 * {@code {"tenant": "...", "purpose": "...", "lawfulBasis": "..."}}.
 * A purpose is authorised for a tenant when a matching document exists.
 */
@Component
@Profile("mongodb")
public class MongoControllerPurposeRegistryAdapter implements ControllerPurposeRegistryPort {

    private static final String COLLECTION = "controller_purpose_registry";

    private final MongoTemplate mongoTemplate;

    public MongoControllerPurposeRegistryAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean isPurposeAuthorized(String tenant, String purpose) {
        if (tenant == null || purpose == null || tenant.isBlank() || purpose.isBlank()) {
            return false;
        }
        // Fail-open when the tenant has NO registered purposes (no declared controller scope).
        // Matches the DefaultControllerPurposeRegistryStub semantics: controller purpose
        // validation only kicks in once a controller has declared its processing scope.
        long tenantCount = mongoTemplate.count(Query.query(
                Criteria.where("tenant").is(tenant)
        ), COLLECTION);
        if (tenantCount == 0) {
            return true;
        }
        long count = mongoTemplate.count(Query.query(
                Criteria.where("tenant").is(tenant).and("purpose").is(purpose)
        ), COLLECTION);
        return count > 0;
    }

    @Override
    public void registerPurpose(String tenant, String purpose, String lawfulBasis) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("tenant", tenant);
        doc.put("purpose", purpose);
        if (lawfulBasis != null && !lawfulBasis.isBlank()) {
            doc.put("lawfulBasis", lawfulBasis);
        }
        mongoTemplate.save(doc, COLLECTION);
    }
}
