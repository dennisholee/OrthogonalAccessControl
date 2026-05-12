package com.oac.decision.adapter.out.policy;

import com.oac.decision.application.port.out.PolicyRegistryPort;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.LookupResourcesRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class InMemoryPolicyRegistryAdapter implements PolicyRegistryPort {

    private static final List<ResourceGrant> RESOURCE_GRANTS = List.of(
            new ResourceGrant("acc-1", "account", "read", "user-1", "tenant-a", "us", "retail", "cards", "staff"),
            new ResourceGrant("acc-2", "account", "read", "user-1", "tenant-a", "us", "retail", "cards", "staff"),
            new ResourceGrant("acc-3", "account", "read", "user-1", "tenant-a", "eu", "retail", "cards", "staff"),
            new ResourceGrant("acc-9", "account", "approve", "user-9", "tenant-a", "us", "retail", "cards", "staff")
    );

    @Override
    public List<String> findMatchedPolicies(CheckPermissionRequest request) {
        List<String> matchedPolicies = new ArrayList<>();
        Map<String, Object> runtime = request.runtimeContext() == null ? Map.of() : request.runtimeContext();

        // Baseline explicit deny signal used for precedence and fail-safe behavior.
        if (Boolean.TRUE.equals(runtime.get("blocked")) || "blocked-user".equals(request.subject().id())) {
            matchedPolicies.add("POL.GLOBAL.ACCESS.DENY.v1");
        }

        // Baseline RBAC-style allow rule for a known reader subject in tenant scope.
        if ("user-reader".equals(request.subject().id())
                && "read".equals(request.action())
                && "account".equals(request.resource().type())
                && "tenant-a".equals(request.boundaryContext().tenant())) {
            matchedPolicies.add("POL.RBAC.ACCOUNT.READ.ALLOW.v1");
        }

        // Baseline PBAC-style allow rule using runtime context and boundary channel.
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

    @Override
    public List<String> findAuthorizedResourceIds(LookupResourcesRequest request) {
        return RESOURCE_GRANTS.stream()
                .filter(grant -> grant.resourceType().equals(request.resourceType()))
                .filter(grant -> grant.action().equals(request.action()))
                .filter(grant -> grant.subjectId().equals(request.subject().id()))
                .filter(grant -> grant.tenant().equals(request.boundaryContext().tenant()))
                .filter(grant -> grant.geography().equals(request.boundaryContext().geography()))
                .filter(grant -> grant.market().equals(request.boundaryContext().market()))
                .filter(grant -> grant.lineOfBusiness().equals(request.boundaryContext().lineOfBusiness()))
                .filter(grant -> grant.channel().equals(request.boundaryContext().channel()))
                .map(ResourceGrant::resourceId)
                .distinct()
                .toList();
    }

    private record ResourceGrant(
            String resourceId,
            String resourceType,
            String action,
            String subjectId,
            String tenant,
            String geography,
            String market,
            String lineOfBusiness,
            String channel
    ) {
    }
}
