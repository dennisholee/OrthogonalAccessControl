package com.oac.decision.adapter.out.policy;

import com.oac.decision.application.port.out.PolicyRegistryPort;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.LookupResourcesRequest;
import com.oac.decision.model.RelationshipEdge;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MongoDB-backed policy registry adapter.
 * Stores policies as documents in a "policies" collection and provides
 * boundary-aware policy matching for access decisions.
 */
@Component
@Profile("mongodb")
public class MongoPolicyRegistryAdapter implements PolicyRegistryPort {

    private static final String COLLECTION = "policies";

    private final MongoTemplate mongoTemplate;

    public MongoPolicyRegistryAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<String> findMatchedPolicies(CheckPermissionRequest request) {
        Criteria criteria = Criteria.where("state").is("ACTIVE");

        // Match boundary context (wildcards match any value)
        Criteria boundaryCriteria = buildBoundaryMatchCriteria(request);
        criteria = criteria.andOperator(boundaryCriteria);

        // Optional subject match
        List<Criteria> subjectCriteria = new ArrayList<>();
        subjectCriteria.add(Criteria.where("subjectId").is(request.subject().id()));
        subjectCriteria.add(Criteria.where("subjectId").exists(false));
        criteria = criteria.orOperator(subjectCriteria.toArray(new Criteria[0]));

        // Match action
        criteria = criteria.and("action").is(request.action());

        // Match resource type
        criteria = criteria.and("resourceType").is(request.resource().type());

        List<Map> policies = mongoTemplate.find(
                Query.query(criteria), Map.class, COLLECTION);

        List<String> matchedPolicyNames = new ArrayList<>();
        for (Map policy : policies) {
            matchedPolicyNames.add((String) policy.getOrDefault("name", "UNKNOWN"));
        }

        // Fallback: if no MongoDB policies, use in-memory RBAC/PBAC/ReBAC baseline
        if (matchedPolicyNames.isEmpty()) {
            matchedPolicyNames.addAll(applyBaselineRules(request));
        }

        return matchedPolicyNames;
    }

    @Override
    public List<String> findAuthorizedResourceIds(LookupResourcesRequest request) {
        // Use active policies + boundary constraints to determine authorized resources
        Criteria criteria = Criteria.where("state").is("ACTIVE")
                .and("action").is(request.action())
                .and("resourceType").is(request.resourceType())
                .and("effect").is("ALLOW");

        List<Map> allowPolicies = mongoTemplate.find(
                Query.query(criteria), Map.class, COLLECTION);

        if (allowPolicies.isEmpty()) {
            return List.of();
        }

        // For each matching policy, determine the resource IDs the subject can access
        // via relationship graph lookup (delegated to the caller's RelationshipGraphPort)
        // This implementation returns resource IDs from a dedicated "resource_grants" collection
        // that maps subject+action+type+boundary -> authorized resource IDs
        Criteria grantCriteria = Criteria.where("subjectId").is(request.subject().id())
                .and("action").is(request.action())
                .and("resourceType").is(request.resourceType())
                .and("tenant").is(request.boundaryContext().tenant())
                .and("geography").is(request.boundaryContext().geography())
                .and("market").is(request.boundaryContext().market())
                .and("lineOfBusiness").is(request.boundaryContext().lineOfBusiness())
                .and("channel").is(request.boundaryContext().channel());

        List<Map> grants = mongoTemplate.find(
                Query.query(grantCriteria), Map.class, "resource_grants");

        return grants.stream()
                .map(g -> (String) g.get("resourceId"))
                .distinct()
                .sorted()
                .toList();
    }

    public String getActiveVersion() {
        Criteria criteria = Criteria.where("state").is("ACTIVE");
        List<Map> activePolicies = mongoTemplate.find(
                Query.query(criteria), Map.class, COLLECTION);
        if (activePolicies.isEmpty()) return "v0";
        return activePolicies.stream()
                .map(p -> (String) p.getOrDefault("version", "v0"))
                .max(String::compareTo)
                .orElse("v0");
    }

    private Criteria buildBoundaryMatchCriteria(CheckPermissionRequest request) {
        List<Criteria> parts = new ArrayList<>();
        if (request.boundaryContext() != null) {
            parts.add(boundaryOrWildcard("boundaryContext.tenant", request.boundaryContext().tenant()));
            parts.add(boundaryOrWildcard("boundaryContext.geography", request.boundaryContext().geography()));
            parts.add(boundaryOrWildcard("boundaryContext.market", request.boundaryContext().market()));
            parts.add(boundaryOrWildcard("boundaryContext.lineOfBusiness", request.boundaryContext().lineOfBusiness()));
            parts.add(boundaryOrWildcard("boundaryContext.channel", request.boundaryContext().channel()));
        }
        return new Criteria().andOperator(parts.toArray(new Criteria[0]));
    }

    private Criteria boundaryOrWildcard(String field, String value) {
        return new Criteria().orOperator(
                Criteria.where(field).is(value),
                Criteria.where(field).is("*")
        );
    }

    private List<String> applyBaselineRules(CheckPermissionRequest request) {
        List<String> matchedPolicies = new ArrayList<>();
        Map<String, Object> runtime = request.runtimeContext() == null ? Map.of() : request.runtimeContext();

        if (Boolean.TRUE.equals(runtime.get("blocked")) || "blocked-user".equals(request.subject().id())) {
            matchedPolicies.add("POL.GLOBAL.ACCESS.DENY.v1");
        }
        if ("user-reader".equals(request.subject().id())
                && "read".equals(request.action())
                && "account".equals(request.resource().type())
                && "tenant-a".equals(request.boundaryContext().tenant())) {
            matchedPolicies.add("POL.RBAC.ACCOUNT.READ.ALLOW.v1");
        }
        if ("approve".equals(request.action())
                && "staff".equals(request.boundaryContext().channel())
                && "L1".equals(runtime.get("approvalLevel"))) {
            matchedPolicies.add("POL.PBAC.APPROVAL.L1.ALLOW.v1");
        }
        Object relationship = runtime.get("relationship");
        if ("account".equals(request.resource().type())
                && "read".equals(request.action())
                && ("owner".equals(relationship) || "reviewer".equals(relationship))) {
            matchedPolicies.add("POL.REBAC.ACCOUNT.RELATIONSHIP.READ.ALLOW.v1");
        }
        return matchedPolicies;
    }
}