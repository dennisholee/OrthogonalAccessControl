package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;
import com.oac.decision.model.CheckPermissionRequest.PrincipalMemberships;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DomainMembershipRule — validates the principal's multi-domain memberships (Section 4.33)
 * against the resource's boundary context.
 * <p>
 * When {@code principalMemberships} is declared in the request, the PDP MUST verify that
 * the resource's boundary values (tenant, geography, market, lineOfBusiness, channel,
 * purpose, regulatoryRegime) are contained within the principal's membership lists for
 * the corresponding dimension. A resource in tenant-b requires the principal to hold
 * {@code tenant-b} in {@code principalMemberships.tenants}.
 * <p>
 * Backward compatibility: when {@code principalMemberships} is absent from the request
 * (null), the rule does not fire — legacy non-CDP requests are unaffected.
 * <p>
 * Decision code: {@code DECISION_DOMAIN_NOT_IN_SCOPE}.
 */
public class DomainMembershipRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        PrincipalMemberships memberships = context.request().principalMemberships();
        if (memberships == null) {
            return Optional.empty();
        }

        // The rule only makes sense when there's an ALLOW candidate to gate.
        if (!context.hasAllow()) {
            return Optional.empty();
        }

        Map<String, Object> rt = context.resolvedRuntimeContext();
        if (rt == null) rt = Map.of();

        // Request domain check (Section 4.33): the principal must also be a member of the
        // REQUEST boundary domain for cross-boundary authorisation to be valid.
        var bc = context.request().boundaryContext();
        if (bc != null) {
            if (checkDimension(bc.tenant(), memberships.tenants(), false, "tenant")) {
                return domainOutcome("tenant");
            }
            if (checkDimension(bc.geography(), memberships.geographies(), false, "geography")) {
                return domainOutcome("geography");
            }
            if (checkDimension(bc.market(), memberships.markets(), false, "market")) {
                return domainOutcome("market");
            }
            if (checkDimension(bc.lineOfBusiness(), memberships.linesOfBusiness(), false, "lineOfBusiness")) {
                return domainOutcome("lineOfBusiness");
            }
            if (checkDimension(bc.channel(), memberships.channels(), false, "channel")) {
                return domainOutcome("channel");
            }
            if (bc.purpose() != null && !bc.purpose().isBlank()
                    && checkDimension(bc.purpose(), memberships.purposes(), false, "purpose")) {
                return domainOutcome("purpose");
            }
            if (bc.regulatoryRegime() != null && !bc.regulatoryRegime().isBlank()
                    && checkDimension(bc.regulatoryRegime(), memberships.regulatoryRegimes(), false, "regulatoryRegime")) {
                return domainOutcome("regulatoryRegime");
            }
        }

        boolean violated = false;
        String violatedDimension = null;

        violated = checkDimension(
                rt.get("resourceTenant"), memberships.tenants(),
                violated, "tenant");
        if (violated) {
            return domainOutcome("tenant");
        }
        violated = checkDimension(
                rt.get("resourceGeography"), memberships.geographies(),
                false, "geography");
        if (violated) {
            return domainOutcome("geography");
        }
        violated = checkDimension(
                rt.get("resourceMarket"), memberships.markets(),
                false, "market");
        if (violated) {
            return domainOutcome("market");
        }
        violated = checkDimension(
                rt.get("resourceLineOfBusiness"), memberships.linesOfBusiness(),
                false, "lineOfBusiness");
        if (violated) {
            return domainOutcome("lineOfBusiness");
        }
        violated = checkDimension(
                rt.get("resourceChannel"), memberships.channels(),
                false, "channel");
        if (violated) {
            return domainOutcome("channel");
        }
        // Purpose and regulatoryRegime are optional dimensions — only validate when
        // the resource declares them in runtime context.
        Object resourcePurpose = rt.get("resourcePurpose");
        if (resourcePurpose != null && !resourcePurpose.toString().isBlank()) {
            if (checkDimension(resourcePurpose, memberships.purposes(), false, "purpose")) {
                return domainOutcome("purpose");
            }
        }
        Object resourceRegime = rt.get("resourceRegulatoryRegime");
        if (resourceRegime != null && !resourceRegime.toString().isBlank()) {
            if (checkDimension(resourceRegime, memberships.regulatoryRegimes(), false, "regulatoryRegime")) {
                return domainOutcome("regulatoryRegime");
            }
        }

        return Optional.empty();
    }

    private Optional<DecisionOutcome> domainOutcome(String dimension) {
        return Optional.of(new DecisionOutcome(
                "DENY",
                "DECISION_DOMAIN_NOT_IN_SCOPE",
                "evidence://decision/domain-not-in-scope/" + dimension
        ));
    }

    /**
     * Checks whether the resource's boundary value is present in the principal's
     * membership list. A missing resource value (null) means no constraint.
     */
    private boolean checkDimension(Object resourceValue, List<String> membershipList,
                                   boolean carryViolation, String dimension) {
        if (resourceValue == null) return carryViolation;
        String value = resourceValue.toString();
        if (value.isBlank()) return carryViolation;
        List<String> list = membershipList == null ? List.of() : membershipList;
        return carryViolation || !list.contains(value);
    }
}