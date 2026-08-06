package com.oac.decision.application.port.shared;

import com.oac.decision.model.BoundaryContext;
import com.oac.decision.model.CheckPermissionRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Database-agnostic policy matcher — the single source of truth for how a policy
 * document matches a {@link CheckPermissionRequest}.
 * <p>
 * Both {@code MongoPolicyRegistryAdapter} and the emulator's in-memory adapter delegate
 * here so that the emulator produces byte-for-byte identical {@code matchedPolicies}
 * to the core build. It replicates the four matching passes in the same order:
 * <ol>
 *   <li>strict match (subject/action/resourceType/boundary)</li>
 *   <li>subject-scoped DENY (effect=DENY, subjectId present, no action)</li>
 *   <li>broad DENY (effect=DENY, no action, no subjectId)</li>
 *   <li>SpEL policies ({@code spelCondition} present, scoped with absent-wildcard semantics)</li>
 *   <li>conditions[] policies (rendered as {@code :COND.} markers)</li>
 * </ol>
 */
public final class PolicyMatcher {

    private static final List<String> CORE_BOUNDARY_FIELDS =
            List.of("tenant", "geography", "market", "lineOfBusiness", "channel");

    private PolicyMatcher() {
    }

    private static final int MAX_INHERITANCE_DEPTH = 3;

    /**
     * Matches all ACTIVE policies against the request and returns the formatted
     * policy entries in the same order the MongoDB adapter produces them.
     */
    public static List<String> match(List<Map<String, Object>> activePolicies, CheckPermissionRequest request) {
        return match(activePolicies, request, List.of());
    }

    /**
     * Matches all ACTIVE policies against the request and returns the formatted
     * policy entries in the same order the MongoDB adapter produces them.
     * <p>
     * {@code policySets} are Policy Set documents (Section 4.2). Policies referenced
     * by a set ({@code policyIds}) are evaluated ONLY through the set — they are
     * excluded from the independent passes and reachable solely via the set's
     * combining algorithm, so that environment/canary scoping on the set is
     * meaningful. When the set is not applicable, its members contribute nothing.
     */
    public static List<String> match(List<Map<String, Object>> activePolicies,
                                     CheckPermissionRequest request,
                                     List<Map<String, Object>> policySets) {
        Set<String> matched = new LinkedHashSet<>();
        // Resolve inheritance chains (Section 4.34) before matching — child policies
        // inherit boundary scope/effect from parents with child-takes-precedence overrides.
        List<Map<String, Object>> resolved = resolveInheritance(activePolicies);

        // Collect set member names — policies owned by a policy set are evaluated only
        // through the set, never independently (Section 4.2 atomic bundle semantics).
        Set<String> setMembers = collectSetMembers(policySets);

        // Pass 1: strict match (Query 1)
        for (Map<String, Object> policy : resolved) {
            if (isComposite(policy)) continue;
            if (setMembers.contains(nameOf(policy))) continue;
            if (!"ACTIVE".equals(policy.get("state"))) continue;
            if (!isEffective(policy)) continue;
            if (!environmentMatches(policy, request)) continue;
            if (!breakGlassEligible(policy, request)) continue;
            if (!subjectMatches(policy, request)) continue;
            if (!actionMatches(policy, request.action(), false)) continue;
            if (!resourceTypeMatches(policy, request.resource().type(), false)) continue;
            if (!boundaryMatches(policy, request.boundaryContext())) continue;
            matched.add(renderStrictEntry(policy));
        }

        // Pass 2: subject-scoped DENY (Query 2) — effect=DENY, subjectId matches, no action constraint
        for (Map<String, Object> policy : resolved) {
            if (isComposite(policy)) continue;
            if (setMembers.contains(nameOf(policy))) continue;
            if (!"ACTIVE".equals(policy.get("state"))) continue;
            if (!isEffective(policy)) continue;
            if (!environmentMatches(policy, request)) continue;
            if (!breakGlassEligible(policy, request)) continue;
            if (!"DENY".equals(policy.get("effect"))) continue;
            if (policy.get("subjectId") == null) continue;
            if (!policy.get("subjectId").toString().equals(request.subject().id())) continue;
            if (policy.get("action") != null) continue;
            matched.add(renderDenyEntry(policy));
        }

        // Pass 3: broad DENY (Query 2b) — effect=DENY, no action, no subjectId
        for (Map<String, Object> policy : resolved) {
            if (isComposite(policy)) continue;
            if (setMembers.contains(nameOf(policy))) continue;
            if (!"ACTIVE".equals(policy.get("state"))) continue;
            if (!isEffective(policy)) continue;
            if (!environmentMatches(policy, request)) continue;
            if (!breakGlassEligible(policy, request)) continue;
            if (!"DENY".equals(policy.get("effect"))) continue;
            if (policy.get("action") != null) continue;
            if (policy.get("subjectId") != null) continue;
            matched.add(renderDenyEntry(policy));
        }

        // Pass 4: SpEL policies (Query 3) — spelCondition present, scoped match
        for (Map<String, Object> policy : resolved) {
            if (isComposite(policy)) continue;
            if (setMembers.contains(nameOf(policy))) continue;
            Object spel = policy.get("spelCondition");
            if (spel == null) continue;
            if (spel instanceof String sc && sc.isBlank()) continue;
            if (!"ACTIVE".equals(policy.get("state"))) continue;
            if (!isEffective(policy)) continue;
            if (!environmentMatches(policy, request)) continue;
            if (!breakGlassEligible(policy, request)) continue;
            if (!subjectMatches(policy, request)) continue;
            if (!actionMatches(policy, request.action(), true)) continue;
            if (!resourceTypeMatches(policy, request.resource().type(), true)) continue;
            if (!boundaryMatches(policy, request.boundaryContext())) continue;
            matched.add(renderSpelEntry(policy));
        }

        // Pass 5: conditions[] policies (Query 4)
        for (Map<String, Object> policy : resolved) {
            if (isComposite(policy)) continue;
            if (setMembers.contains(nameOf(policy))) continue;
            Object conditions = policy.get("conditions");
            if (!(conditions instanceof List<?> conditionList) || conditionList.isEmpty()) continue;
            if (!"ACTIVE".equals(policy.get("state"))) continue;
            if (!isEffective(policy)) continue;
            if (!environmentMatches(policy, request)) continue;
            if (!breakGlassEligible(policy, request)) continue;
            String rendered = renderConditions(conditionList);
            if (rendered.isEmpty()) continue;
            String effect = str(policy, "effect", "ALLOW");
            String name = str(policy, "name", "UNKNOWN");
            matched.add("POL." + effect + "." + name + ":COND." + rendered);
        }

        // Pass 6: composite policies (Section 4.35) — AND/OR/NOT operators over
        // referenced policies, evaluated within the same decision context.
        java.util.Map<String, Map<String, Object>> byName = new java.util.LinkedHashMap<>();
        for (Map<String, Object> policy : resolved) {
            Object name = policy.get("name");
            if (name != null) byName.put(name.toString(), policy);
        }
        for (Map<String, Object> policy : resolved) {
            if (!isComposite(policy)) continue;
            if (setMembers.contains(nameOf(policy))) continue;
            if (!"ACTIVE".equals(policy.get("state"))) continue;
            if (!isEffective(policy)) continue;
            if (!environmentMatches(policy, request)) continue;
            if (!breakGlassEligible(policy, request)) continue;
            if (!subjectMatches(policy, request)) continue;
            if (!actionMatches(policy, request.action(), true)) continue;
            if (!resourceTypeMatches(policy, request.resource().type(), true)) continue;
            if (!boundaryMatches(policy, request.boundaryContext())) continue;
            if (compositionMatches(policy, byName, matched, request, new java.util.HashSet<>())) {
                matched.add(renderStrictEntry(policy));
            }
        }

        // Pass 7: policy sets (Section 4.2). A set applies when its scope checks pass
        // (environment, canary) AND at least one constituent matches. The combining
        // algorithm yields a single effect; constituent entries are suppressed and
        // replaced by one set entry — so denyOverrides/permitOverrides/firstApplicable/
        // onlyOneApplicable are enforced at the bundle level.
        for (Map<String, Object> set : policySets) {
            if (!setApplies(set, request)) continue;
            String setId = str(set, "setId", "UNKNOWN");
            List<String> memberMatched = new ArrayList<>();
            List<String> memberIds = toList(set.get("policyIds"));
            for (String pid : memberIds) {
                Map<String, Object> member = byName.get(pid);
                if (member == null) continue;
                if (!"ACTIVE".equals(member.get("state"))) continue;
                if (!isEffective(member)) continue;
                if (!environmentMatches(member, request)) continue;
                if (!breakGlassEligible(member, request)) continue;
                if (!subjectMatches(member, request)) continue;
                if (!actionMatches(member, request.action(), true)) continue;
                if (!resourceTypeMatches(member, request.resource().type(), true)) continue;
                if (!boundaryMatches(member, request.boundaryContext())) continue;
                memberMatched.add(renderStrictEntry(member));
            }
            if (memberMatched.isEmpty()) continue;
            String combinedEffect = combineEffects(set, memberMatched, memberIds);
            if (combinedEffect == null) continue;
            matched.removeAll(memberMatched);
            matched.add("POL." + combinedEffect + ".SET." + setId);
        }

        return new ArrayList<>(matched);
    }

    // -------- Policy Set helpers (Section 4.2) --------

    private static Set<String> collectSetMembers(List<Map<String, Object>> policySets) {
        Set<String> members = new java.util.HashSet<>();
        for (Map<String, Object> set : policySets) {
            for (String pid : toList(set.get("policyIds"))) {
                members.add(pid);
            }
        }
        return members;
    }

    /**
     * Scope checks for a policy set: environment matching (Section 4.31) and canary
     * selection (Section 4.2). A set with no declared scope always applies.
     */
    private static boolean setApplies(Map<String, Object> set, CheckPermissionRequest request) {
        Object env = set.get("environment");
        if (env != null) {
            Map<String, Object> rt = request.runtimeContext();
            Object reqEnv = rt == null ? null
                    : rt.getOrDefault("environment", rt.get("deploymentEnvironment"));
            if (!env.toString().equals(reqEnv == null ? null : reqEnv.toString())) {
                return false;
            }
        }
        Object canaryObj = set.get("canary");
        if (canaryObj instanceof Map<?, ?> canary) {
            if (Boolean.TRUE.equals(canary.get("enabled"))) {
                String target = canary.get("target") == null ? "" : canary.get("target").toString();
                if ("by-tenant".equals(target)) {
                    List<String> targetValues = toList(canary.get("targetValues"));
                    String tenant = request.boundaryContext() != null
                            ? request.boundaryContext().tenant() : null;
                    if (tenant == null || !targetValues.contains(tenant)) {
                        return false;
                    }
                } else if ("by-percentage".equals(target)) {
                    int percentage = canary.get("percentage") instanceof Number n ? n.intValue() : 0;
                    String subjectId = request.subject() != null ? request.subject().id() : "";
                    if (Math.abs(subjectId.hashCode()) % 100 >= percentage) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Combining algorithm evaluation over the set's matched constituent entries
     * (Section 4.2). Returns the combined effect or {@code null} when the set cannot
     * produce a decision.
     */
    private static String combineEffects(Map<String, Object> set, List<String> memberMatched,
                                         List<String> memberIds) {
        String algorithm = str(set, "combiningAlgorithm", "denyOverrides");
        // Effects of matched members in policyIds order
        List<String> matchedEffects = new ArrayList<>();
        for (String pid : memberIds) {
            String dotPid = "." + pid;
            for (String entry : memberMatched) {
                if (!entry.contains(dotPid)) continue;
                matchedEffects.add(entry.startsWith("POL.DENY.") ? "DENY" : "ALLOW");
                break;
            }
        }
        return switch (algorithm) {
            case "denyOverrides" -> {
                if (matchedEffects.contains("DENY")) yield "DENY";
                if (matchedEffects.contains("ALLOW")) yield "ALLOW";
                yield null;
            }
            case "permitOverrides" -> {
                if (matchedEffects.contains("ALLOW")) yield "ALLOW";
                if (matchedEffects.contains("DENY")) yield "DENY";
                yield null;
            }
            case "firstApplicable" -> matchedEffects.isEmpty() ? null : matchedEffects.get(0);
            case "onlyOneApplicable" -> matchedEffects.size() == 1 ? matchedEffects.get(0) : "DENY";
            default -> null;
        };
    }

    private static List<String> toList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) result.add(item.toString());
            }
            return result;
        }
        return List.of();
    }

    private static String nameOf(Map<String, Object> policy) {
        return str(policy, "name", "UNKNOWN");
    }

    /**
     * Composition evaluation (Section 4.35): a composite policy matches when its
     * {@code composition.operator} evaluates against the matched status of the referenced
     * policies. AND requires all, OR requires at least one, NOT requires the single
     * referenced policy to be absent from the matched set. Nested composites resolve
     * recursively with cycle protection.
     */
    private static boolean compositionMatches(
            Map<String, Object> composite,
            java.util.Map<String, Map<String, Object>> byName,
            Set<String> matched,
            CheckPermissionRequest request,
            Set<String> visited
    ) {
        Object compObj = composite.get("composition");
        if (!(compObj instanceof Map<?, ?> comp)) return false;
        Object operatorObj = comp.get("operator");
        Object policiesObj = comp.get("policies");
        if (!(policiesObj instanceof List<?> refs)) return false;
        String operator = operatorObj == null ? "" : operatorObj.toString();
        List<Object> refList = new ArrayList<>(refs);

        return switch (operator) {
            case "AND" -> refList.stream().allMatch(
                    ref -> referencedMatches(ref, byName, matched, request, visited));
            case "OR" -> refList.stream().anyMatch(
                    ref -> referencedMatches(ref, byName, matched, request, visited));
            case "NOT" -> refList.size() == 1
                    && !referencedMatches(refList.get(0), byName, matched, request, visited);
            default -> false;
        };
    }

    private static boolean referencedMatches(
            Object ref,
            java.util.Map<String, Map<String, Object>> byName,
            Set<String> matched,
            CheckPermissionRequest request,
            Set<String> visited
    ) {
        if (ref == null) return false;
        String refName = ref.toString();
        // A referenced policy matches if it was matched by the simple passes (rendered
        // entry contains the policy name) OR its own composite evaluates true.
        boolean alreadyMatched = matched.stream()
                .anyMatch(entry -> entry.contains("." + refName));
        if (alreadyMatched) return true;
        if (visited.contains(refName)) return false; // cycle guard
        Map<String, Object> refPolicy = byName.get(refName);
        if (refPolicy == null) return false;
        if (!isComposite(refPolicy)) return false;
        visited.add(refName);
        boolean result = compositionMatches(refPolicy, byName, matched, request, visited);
        visited.remove(refName);
        return result;
    }

    private static boolean isComposite(Map<String, Object> policy) {
        return policy.get("composition") instanceof Map<?, ?>;
    }

    /**
     * Certification due-date check (Section 4.9): a policy is expired when its
     * {@code certification.nextCertificationDate} is in the past AND no active waiver
     * (waiver {@code expiryDate} not yet passed) is recorded. Expired policies still
     * evaluate normally — the PDP emits a WARN audit event instead of blocking.
     */
    public static boolean isCertificationExpired(Map<String, Object> policy, java.time.LocalDate today) {
        Object cert = policy.get("certification");
        if (!(cert instanceof Map<?, ?> certMap)) return false;
        Object nextDate = certMap.get("nextCertificationDate");
        if (nextDate == null) return false;
        java.time.LocalDate due;
        try {
            due = java.time.LocalDate.parse(nextDate.toString());
        } catch (Exception e) {
            // Malformed nextCertificationDate is treated as governance-degraded — warn
            return true;
        }
        if (!today.isAfter(due)) return false; // not yet due
        Object waiverObj = certMap.get("waiver");
        if (waiverObj instanceof Map<?, ?> waiver) {
            Object expiry = waiver.get("expiryDate");
            if (expiry != null) {
                try {
                    java.time.LocalDate waiverExpiry = java.time.LocalDate.parse(expiry.toString());
                    if (!today.isAfter(waiverExpiry)) return false; // active waiver
                } catch (Exception e) {
                    // Malformed waiver expiry treated as no waiver — warn
                }
            }
        }
        return true;
    }

    /**
     * Shadow evaluation (Section 4.42): evaluates DRAFT policies with
     * {@code shadowEvaluation: true} against the request using the same matching passes,
     * returning the names of policies that WOULD have matched. Shadow results must NOT
     * affect the enforced decision — this method is used only to record hypothetical
     * outcomes for the shadow-decisions audit trail.
     */
    public static List<String> matchShadowPolicies(
            List<Map<String, Object>> policies, CheckPermissionRequest request) {
        List<String> shadowMatched = new ArrayList<>();
        for (Map<String, Object> policy : policies) {
            if (!"DRAFT".equals(policy.get("state"))) continue;
            if (!Boolean.TRUE.equals(policy.get("shadowEvaluation"))) continue;
            if (!environmentMatches(policy, request)) continue;
            if (!subjectMatches(policy, request)) continue;
            if (!actionMatches(policy, request.action(), true)) continue;
            if (!resourceTypeMatches(policy, request.resource().type(), true)) continue;
            if (!boundaryMatches(policy, request.boundaryContext())) continue;
            shadowMatched.add(str(policy, "name", "UNKNOWN"));
        }
        return shadowMatched;
    }

    /**
     * Break-glass eligibility (Section 4.5): policies typed {@code BREAK_GLASS}
     * (or whose name contains {@code BREAK.GLASS} for backward compatibility) only
     * match when:
     * <ol>
     *   <li>the request declares {@code breakGlassActive: true} in runtime context, AND</li>
     *   <li>the activation window is ≤ 24 hours (effectiveUntil − effectiveFrom, or
     *       effectiveUntil − now when no effectiveFrom).</li>
     * </ol>
     * This prevents STANDARD policies from being elevated by the flag and ensures
     * break-glass elevation is explicit and time-bounded.
     */
    /**
     * Environment scoping (Section 4.31): a policy that declares {@code environment}
     * matches ONLY requests whose runtime context declares the same environment
     * ({@code environment} or {@code deploymentEnvironment}). A policy without an
     * {@code environment} field is not environment-scoped and matches requests in any
     * environment (backward compatible).
     */
    private static boolean environmentMatches(Map<String, Object> policy, CheckPermissionRequest request) {
        Object env = policy.get("environment");
        if (env == null) {
            return true;
        }
        Map<String, Object> rt = request.runtimeContext();
        Object reqEnv = rt == null ? null
                : rt.getOrDefault("environment", rt.get("deploymentEnvironment"));
        return env.toString().equals(reqEnv == null ? null : reqEnv.toString());
    }

    private static boolean breakGlassEligible(Map<String, Object> policy, CheckPermissionRequest request) {
        boolean isBreakGlass = "BREAK_GLASS".equals(policy.get("policyType"))
                || (policy.get("name") != null
                    && policy.get("name").toString().contains("BREAK.GLASS"));
        if (!isBreakGlass) return true; // non-break-glass policies always eligible
        Object bg = request.runtimeContext() == null
                ? null : request.runtimeContext().get("breakGlassActive");
        boolean active = "true".equalsIgnoreCase(String.valueOf(bg));
        if (!active) return false;
        // 24-hour hard limit (Section 4.5): effectiveUntil − effectiveFrom (or now) ≤ 24h
        return within24HourLimit(policy);
    }

    private static final java.time.Duration BREAK_GLASS_MAX_WINDOW = java.time.Duration.ofHours(24);

    private static boolean within24HourLimit(Map<String, Object> policy) {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant from = extractInstant(policy, "effectiveFrom");
        java.time.Instant until = extractInstant(policy, "effectiveUntil");
        if (until == null) {
            // No explicit expiry — the policy must be considered within-limit only if
            // activation was recent; for safety, treat as valid (the isEffective filter
            // still applies). Administrators MUST set effectiveUntil per Section 4.5.
            return true;
        }
        java.time.Instant start = from != null ? from : now;
        java.time.Duration window = java.time.Duration.between(start, until);
        // Allow small clock skew; strictly enforce ≤ 24h + 1 minute tolerance.
        return window.compareTo(BREAK_GLASS_MAX_WINDOW.plusMinutes(1)) <= 0;
    }

    /**
     * Effective-period filter (Section 4.4): policies with {@code effectiveFrom} in the
     * future or {@code effectiveUntil} in the past MUST NOT be evaluated. Policies without
     * either field are always effective.
     */
    private static boolean isEffective(Map<String, Object> policy) {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant from = extractInstant(policy, "effectiveFrom");
        if (from != null && now.isBefore(from)) return false;
        java.time.Instant until = extractInstant(policy, "effectiveUntil");
        if (until != null && now.isAfter(until)) return false;
        return true;
    }

    private static java.time.Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return java.time.Instant.from(java.time.format.DateTimeFormatter.ISO_DATE_TIME.parse(value));
        } catch (Exception e) {
            return null;
        }
    }

    /** Extracts an Instant from a policy document field (String, Date, or Instant). */
    private static java.time.Instant extractInstant(Map<String, Object> policy, String key) {
        Object value = policy.get(key);
        if (value == null) return null;
        if (value instanceof java.time.Instant instant) return instant;
        if (value instanceof java.util.Date date) return date.toInstant();
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.time.ZonedDateTime zdt) return zdt.toInstant();
        return parseInstant(value.toString());
    }

    // -------- matching helpers --------

    /**
     * Resolves policy inheritance chains (Section 4.34). Each policy with an
     * {@code inheritsFrom} field is merged with its ancestors: the root parent's
     * fields are the base, intermediate and leaf {@code overrides} maps and the
     * child's own fields take precedence (child-takes-precedence). Depth is bounded
     * at {@value #MAX_INHERITANCE_DEPTH}; missing parents and cycles fall back to
     * the standalone policy document.
     */
    private static List<Map<String, Object>> resolveInheritance(List<Map<String, Object>> policies) {
        // Index by name for parent lookup
        Map<String, Map<String, Object>> byName = new java.util.LinkedHashMap<>();
        for (Map<String, Object> policy : policies) {
            Object name = policy.get("name");
            if (name != null) byName.put(name.toString(), policy);
        }

        List<Map<String, Object>> resolved = new ArrayList<>(policies.size());
        for (Map<String, Object> policy : policies) {
            Object parentRef = policy.get("inheritsFrom");
            if (parentRef == null) {
                resolved.add(policy);
                continue;
            }
            resolved.add(mergeInheritance(policy, byName, 0, new java.util.HashSet<>()));
        }
        return resolved;
    }

    private static Map<String, Object> mergeInheritance(
            Map<String, Object> child,
            Map<String, Map<String, Object>> byName,
            int depth,
            Set<String> visited
    ) {
        Object parentRef = child.get("inheritsFrom");
        if (parentRef == null || depth >= MAX_INHERITANCE_DEPTH) {
            return effectivePolicy(child, null);
        }
        String parentName = parentRef.toString();
        if (visited.contains(parentName)) {
            // Cycle — treat child as standalone
            return effectivePolicy(child, null);
        }
        visited.add(parentName);
        Map<String, Object> parent = byName.get(parentName);
        if (parent == null) {
            // Missing parent — child stands alone
            return effectivePolicy(child, null);
        }
        Map<String, Object> parentEffective = mergeInheritance(parent, byName, depth + 1, visited);
        return effectivePolicy(child, parentEffective);
    }

    /**
     * Produces the effective policy document: inherited fields from the parent form the
     * base, then the child's {@code overrides} map is applied, then the child's own
     * explicitly-set fields take precedence.
     */
    private static Map<String, Object> effectivePolicy(Map<String, Object> child, Map<String, Object> parent) {
        Map<String, Object> effective = new java.util.LinkedHashMap<>();
        if (parent != null) {
            effective.putAll(parent);
        }
        effective.putAll(child); // child's own fields take precedence
        // Apply child's overrides after own fields (overrides win over inherited)
        Object overridesObj = child.get("overrides");
        if (overridesObj instanceof Map<?, ?> overrides) {
            for (Map.Entry<?, ?> entry : overrides.entrySet()) {
                effective.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return effective;
    }

    private static boolean subjectMatches(Map<String, Object> policy, CheckPermissionRequest request) {
        Object policySubject = policy.get("subjectId");
        return policySubject == null || policySubject.toString().equals(request.subject().id());
    }

    private static boolean actionMatches(Map<String, Object> policy, String action, boolean allowAbsent) {
        Object policyAction = policy.get("action");
        if (policyAction == null) return allowAbsent;
        String pa = policyAction.toString();
        return "*".equals(pa) || pa.equals(action);
    }

    private static boolean resourceTypeMatches(Map<String, Object> policy, String resourceType, boolean allowAbsent) {
        Object policyResType = policy.get("resourceType");
        boolean singleMatch = false;
        if (policyResType != null) {
            String pr = policyResType.toString();
            singleMatch = "*".equals(pr) || pr.equals(resourceType);
        }
        // Section 4.37 multi-resource support: `resourceTypes` array (IN semantics, OR with
        // the single `resourceType` field per Section 3.2 "ResourceType match").
        Object resTypes = policy.get("resourceTypes");
        if (resTypes instanceof List<?> rtList) {
            for (Object item : rtList) {
                if (item != null
                        && ("*".equals(item.toString()) || item.toString().equals(resourceType))) {
                    return true;
                }
            }
            return singleMatch;
        }
        if (policyResType != null) {
            return singleMatch;
        }
        return allowAbsent;
    }

    private static boolean boundaryMatches(Map<String, Object> policy, BoundaryContext boundary) {
        if (boundary == null) return true;
        for (String field : CORE_BOUNDARY_FIELDS) {
            if (!boundaryFieldMatches(policy, field, boundaryValue(boundary, field))) return false;
        }
        if (boundary.purpose() != null && !boundary.purpose().isBlank()
                && !boundaryFieldMatches(policy, "purpose", boundary.purpose())) {
            return false;
        }
        if (boundary.regulatoryRegime() != null && !boundary.regulatoryRegime().isBlank()
                && !boundaryFieldMatches(policy, "regulatoryRegime", boundary.regulatoryRegime())) {
            return false;
        }
        return true;
    }

    private static String boundaryValue(BoundaryContext boundary, String field) {
        return switch (field) {
            case "tenant" -> boundary.tenant();
            case "geography" -> boundary.geography();
            case "market" -> boundary.market();
            case "lineOfBusiness" -> boundary.lineOfBusiness();
            case "channel" -> boundary.channel();
            default -> null;
        };
    }

    /**
     * Mirrors the MongoDB {@code boundaryMatch} criteria: field equals the request value,
     * contains it (multi-value array scoping), is {@code "*"}, or is absent.
     */
    private static boolean boundaryFieldMatches(Map<String, Object> policy, String field, String requestValue) {
        Object policyValue = policy.get(field);
        if (policyValue == null) return true;
        if (policyValue instanceof List<?> list) {
            return list.stream().anyMatch(item -> item != null && item.toString().equals(requestValue));
        }
        String pv = policyValue.toString();
        return "*".equals(pv) || pv.equals(requestValue);
    }

    // -------- rendering helpers (must match MongoPolicyRegistryAdapter formatting) --------

    private static String renderStrictEntry(Map<String, Object> policy) {
        String effect = str(policy, "effect", "ALLOW");
        String name = str(policy, "name", "UNKNOWN");
        StringBuilder entry = new StringBuilder("POL." + effect + "." + name);
        Object spel = policy.get("spelCondition");
        if (spel instanceof String sc && !sc.isBlank()) {
            entry.append(":").append(sc);
        }
        Object rel = policy.get("requiredRelationship");
        if (rel instanceof String rr && !rr.isBlank()) {
            entry.append(":REBAC.").append(rr);
            // Append boundary scope for composable-domain traversal (Section 4.36)
            Object scope = policy.get("relationshipBoundaryScope");
            if (scope instanceof Map<?, ?> scopeMap && !scopeMap.isEmpty()) {
                StringBuilder scopeStr = new StringBuilder(":SCOPE.");
                boolean first = true;
                for (Map.Entry<?, ?> e : scopeMap.entrySet()) {
                    if (!first) scopeStr.append(",");
                    scopeStr.append(e.getKey()).append("=").append(e.getValue());
                    first = false;
                }
                entry.append(scopeStr);
            }
        }
        return entry.toString();
    }

    private static String renderDenyEntry(Map<String, Object> policy) {
        return "POL.DENY." + str(policy, "name", "UNKNOWN");
    }

    private static String renderSpelEntry(Map<String, Object> policy) {
        String effect = str(policy, "effect", "ALLOW");
        String name = str(policy, "name", "UNKNOWN");
        return "POL." + effect + "." + name + ":" + str(policy, "spelCondition", "");
    }

    private static String renderConditions(List<?> conditionList) {
        List<String> segments = new ArrayList<>();
        for (Object item : conditionList) {
            if (!(item instanceof Map<?, ?> condition)) continue;
            String type = condition.get("type") == null ? "" : condition.get("type").toString();
            Object paramsObj = condition.get("params");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = paramsObj instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            String value = switch (type) {
                case "spel" -> params.get("expression") == null ? "" : params.get("expression").toString();
                case "timeWindow" -> {
                    String window = params.get("window") == null ? "" : params.get("window").toString();
                    String tz = params.get("timezone") == null ? "" : params.get("timezone").toString();
                    yield (window + " " + tz).trim();
                }
                case "sourceIp" -> params.get("cidr") == null ? "" : params.get("cidr").toString();
                case "rebac" -> params.get("relationshipType") == null ? "" : params.get("relationshipType").toString();
                default -> null;
            };
            if (value != null && !value.isBlank()) {
                segments.add(type + "=" + value);
            }
        }
        return String.join("|", segments);
    }

    private static String str(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultVal;
    }
}
