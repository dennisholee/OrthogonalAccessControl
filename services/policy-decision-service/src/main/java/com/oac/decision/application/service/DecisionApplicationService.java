package com.oac.decision.application.service;

import com.oac.decision.application.port.in.DecisionQueryUseCase;
import com.oac.decision.application.port.out.AttributeResolverPort;
import com.oac.decision.application.port.out.AuditEvidencePort;
import com.oac.decision.application.port.out.FailOpenEndpointPolicyPort;
import com.oac.decision.application.port.out.ObservabilityPort;
import com.oac.decision.application.port.out.PolicyRegistryPort;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;
import com.oac.decision.application.service.decision.rules.AllowRule;
import com.oac.decision.application.service.decision.rules.BoundaryViolationRule;
import com.oac.decision.application.service.decision.rules.ConsistencyTokenRule;
import com.oac.decision.application.service.decision.rules.DefaultDenyRule;
import com.oac.decision.application.service.decision.rules.DependencyOutageRule;
import com.oac.decision.application.service.decision.rules.ExplicitDenyRule;
import com.oac.decision.application.service.decision.rules.MissingBoundaryContextRule;
import com.oac.decision.model.AuditEventRecord;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.CheckPermissionResponse;
import com.oac.decision.model.LookupResourcesRequest;
import com.oac.decision.model.LookupResourcesResponse;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DecisionApplicationService implements DecisionQueryUseCase {

    private static final List<DecisionRule> RULES = List.of(
            new ExplicitDenyRule(),
            new MissingBoundaryContextRule(),
            new BoundaryViolationRule(),
            new DependencyOutageRule(),
            new ConsistencyTokenRule(),
            new AllowRule(),
            new DefaultDenyRule()
    );

    private final PolicyRegistryPort policyRegistryPort;
    private final AttributeResolverPort attributeResolverPort;
    private final AuditEvidencePort auditEvidencePort;
    private final ObservabilityPort observabilityPort;
    private final FailOpenEndpointPolicyPort failOpenEndpointPolicyPort;

    public DecisionApplicationService(
            PolicyRegistryPort policyRegistryPort,
            AttributeResolverPort attributeResolverPort,
            AuditEvidencePort auditEvidencePort,
            ObservabilityPort observabilityPort,
            FailOpenEndpointPolicyPort failOpenEndpointPolicyPort
    ) {
        this.policyRegistryPort = policyRegistryPort;
        this.attributeResolverPort = attributeResolverPort;
        this.auditEvidencePort = auditEvidencePort;
        this.observabilityPort = observabilityPort;
        this.failOpenEndpointPolicyPort = failOpenEndpointPolicyPort;
    }

    @Override
    public CheckPermissionResponse checkPermission(CheckPermissionRequest request) {
        List<String> matchedPolicies = policyRegistryPort.findMatchedPolicies(request);
        Map<String, Object> resolvedRuntimeContext = new HashMap<>(attributeResolverPort.resolve(request));
        resolvedRuntimeContext.put(
            "failOpenEndpointApproved",
            failOpenEndpointPolicyPort.isFailOpenApproved(request.endpointKey())
        );
        boolean hasAllow = matchedPolicies.stream().anyMatch(policy -> policy.contains(".ALLOW."));

        DecisionContext context = new DecisionContext(
                request,
                matchedPolicies,
                resolvedRuntimeContext,
                hasAllow
        );

        DecisionOutcome outcome = RULES.stream()
                .map(rule -> rule.evaluate(context))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst()
                .orElseGet(() -> new DecisionOutcome(
                        "DENY",
                        "DECISION_DEFAULT_DENY",
                        "evidence://decision/default-deny"
                ));

        CheckPermissionResponse response = new CheckPermissionResponse(
                outcome.decision(),
                outcome.decisionCode(),
                matchedPolicies,
                List.of(),
                List.of(outcome.evidenceRef()),
                OffsetDateTime.now()
        );

        Number checkRegionalLag = asNumber(resolvedRuntimeContext.get("simulatedRegionalLagMs"));
        if (checkRegionalLag != null) {
            observabilityPort.recordRegionalLag("check-permission", checkRegionalLag.longValue());
        }
        Number checkReplicaVersion = asNumber(resolvedRuntimeContext.get("replicaVersion"));
        Number checkMinimumReplicaVersion = asNumber(resolvedRuntimeContext.get("minimumReplicaVersion"));
        if (checkReplicaVersion != null && checkMinimumReplicaVersion != null) {
            observabilityPort.recordReplicaVersionGap(
                    "check-permission",
                    Math.max(checkMinimumReplicaVersion.longValue() - checkReplicaVersion.longValue(), 0)
            );
        }

            if (response.decisionCode().startsWith("DECISION_FAIL_OPEN")
                || "DECISION_FAIL_CLOSED_DEPENDENCY_OUTAGE".equals(response.decisionCode())
                || "DECISION_FAIL_CLOSED_STRICT_CONSISTENCY".equals(response.decisionCode())
                || "DECISION_REGIONAL_REPLICA_LAG".equals(response.decisionCode())
                || "DECISION_REGIONAL_REPLICA_VERSION_STALE".equals(response.decisionCode())) {
                observabilityPort.recordSecurityAlert(response.decisionCode());
            }

        observabilityPort.recordDecision(response.decisionCode());
        String traceEntityId = request.requestId() == null || request.requestId().isBlank()
            ? "decision-" + UUID.randomUUID()
            : request.requestId();
        auditEvidencePort.append(new AuditEventRecord(
            UUID.randomUUID().toString(),
            "DECISION_EVALUATED",
            "DECISION",
            traceEntityId,
            request.subject().id(),
            response.decisionCode(),
            response.explanationRefs().isEmpty() ? null : response.explanationRefs().get(0),
            "INFO",
            OffsetDateTime.now()
        ));

        return response;
    }

    @Override
    public LookupResourcesResponse lookupResources(LookupResourcesRequest request) {
        if (Boolean.TRUE.equals(request.strictConsistency())) {
            if (request.simulatedRegionalLagMs() != null) {
                observabilityPort.recordRegionalLag("lookup-resources", request.simulatedRegionalLagMs());
            }
            if (request.minimumReplicaVersion() != null && request.replicaVersion() != null) {
                observabilityPort.recordReplicaVersionGap(
                        "lookup-resources",
                        Math.max(request.minimumReplicaVersion() - request.replicaVersion(), 0)
                );
            }
            if (request.simulatedRegionalLagMs() != null && request.simulatedRegionalLagMs() > 250) {
                observabilityPort.recordSecurityAlert("LOOKUP_STRICT_CONSISTENCY_REGIONAL_LAG");
                return new LookupResourcesResponse(List.of(), null);
            }
            if (request.minimumReplicaVersion() != null
                    && request.replicaVersion() != null
                    && request.replicaVersion() < request.minimumReplicaVersion()) {
                observabilityPort.recordSecurityAlert("LOOKUP_STRICT_CONSISTENCY_REPLICA_VERSION_STALE");
                return new LookupResourcesResponse(List.of(), null);
            }
            if (request.consistencyToken() == null || request.consistencyToken().isBlank()) {
                observabilityPort.recordSecurityAlert("LOOKUP_STRICT_CONSISTENCY_TOKEN_REQUIRED");
                return new LookupResourcesResponse(List.of(), null);
            }
            if (request.requiredConsistencyToken() == null
                    || request.requiredConsistencyToken().isBlank()
                    || !request.requiredConsistencyToken().equals(request.consistencyToken())) {
                observabilityPort.recordSecurityAlert("LOOKUP_STRICT_CONSISTENCY_TOKEN_MISMATCH");
                return new LookupResourcesResponse(List.of(), null);
            }
        }

        List<String> allAuthorizedResourceIds = policyRegistryPort.findAuthorizedResourceIds(request)
            .stream()
            .sorted()
            .toList();

        int pageSize = request.pageSize() == null ? 100 : request.pageSize();
        int startIndex = parsePageToken(request.pageToken());
        if (startIndex >= allAuthorizedResourceIds.size()) {
            return new LookupResourcesResponse(List.of(), null);
        }

        int endIndex = Math.min(startIndex + pageSize, allAuthorizedResourceIds.size());
        List<String> page = allAuthorizedResourceIds.subList(startIndex, endIndex);
        String nextPageToken = endIndex < allAuthorizedResourceIds.size() ? String.valueOf(endIndex) : null;

        return new LookupResourcesResponse(page, nextPageToken);
    }

    private int parsePageToken(String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(pageToken);
            return Math.max(parsed, 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Number asNumber(Object value) {
        return value instanceof Number number ? number : null;
    }
}
