package com.oac.decision.application.port.shared;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Database-agnostic helper for extracting SpEL attribute references from policy
 * conditions (Section 4.7 Attribute Schema Registry).
 * <p>
 * Also exposes the matched-policy → SpEL-expression extraction shared by the rules
 * that evaluate flat SpEL policies ({@code SpelConditionRule},
 * {@code RequiredAttributeRule}), keeping the single source of truth for the
 * {@code POL.{EFFECT}.{name}:{expression}} string protocol.
 */
public final class AttributeReferenceExtractor {

    private static final Set<String> ROOTS = Set.of(
            "subject", "resource", "environment", "principalMemberships",
            "#subject", "#resource", "#environment", "#principalMemberships");

    /** Matches root-qualified references: {@code subject.department}, {@code #resource.dataSources}. */
    private static final Pattern ATTR_REF = Pattern.compile(
            "(?:^|[^\\w])(#?(?:subject|resource|environment|principalMemberships))\\.([a-zA-Z_][a-zA-Z0-9_]*)");

    private AttributeReferenceExtractor() {
    }

    /**
     * Extract canonical attribute references (e.g. {@code resource.dataSubjectCategory})
     * from a SpEL expression, in first-appearance order without duplicates. The leading
     * {@code #} is normalized away so lookups use the stored-policy form.
     */
    public static List<String> extract(String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        Set<String> refs = new LinkedHashSet<>();
        Matcher matcher = ATTR_REF.matcher(expression);
        while (matcher.find()) {
            String root = matcher.group(1);
            String path = matcher.group(2);
            String normalizedRoot = root.startsWith("#") ? root.substring(1) : root;
            if (ROOTS.contains(normalizedRoot)) {
                refs.add(normalizedRoot + "." + path);
            }
        }
        return new ArrayList<>(refs);
    }

    /**
     * Extracts the inline SpEL expression from a matched policy entry
     * ({@code POL.{EFFECT}.{name}:{expression}}). Returns {@code null} when the entry
     * carries no inline expression (typed conditions[] markers, plain names, etc.).
     */
    public static String extractSpelExpression(String policy) {
        if (policy == null || policy.isBlank()) {
            return null;
        }
        // Policies rendered with typed conditions[] markers are handled by
        // ConditionCompositionRule — never evaluate them as a flat SpEL string.
        if (policy.contains(":COND.")) {
            return null;
        }
        String[] parts = policy.split(":", 2);
        if (parts.length < 2) {
            return null;
        }
        String expression = parts[1].trim();
        if (expression.startsWith("POL.") || expression.startsWith("FIELD.")
                || expression.startsWith("REBAC.") || expression.startsWith("WORKLOAD.")
                || expression.startsWith("BREAK.") || expression.startsWith("E2E.")
                || expression.startsWith("SET.")) {
            return null;
        }
        return expression;
    }
}
