package com.oac.enforcement.resolver;

import com.oac.enforcement.OacEntitlementConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * Composite subject resolver that chains multiple {@link SubjectResolver}
 * instances in {@link Ordered} order and returns the first non-null result.
 *
 * <p>This resolver is automatically wired when {@code resolver-mode: composite}
 * is configured. It collects all {@link SubjectResolver} beans (including
 * {@link SubjectResolverDelegate} instances) from the application context
 * and iterates them in ascending order of {@link #getOrder()}.</p>
 */
public class CompositeSubjectResolver implements SubjectResolver {

    private final List<SubjectResolver> resolvers;

    public CompositeSubjectResolver(List<SubjectResolver> resolvers) {
        this.resolvers = resolvers;
        this.resolvers.sort(OrderComparator.INSTANCE);
    }

    @Override
    public String resolve(HttpServletRequest request, OacEntitlementConfig config) {
        for (SubjectResolver resolver : resolvers) {
            String subjectId = resolver.resolve(request, config);
            if (subjectId != null && !subjectId.isBlank()) {
                return subjectId;
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}