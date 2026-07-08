package com.oac.enforcement.resolver;

/**
 * Extension point interface for custom subject resolution logic.
 *
 * <p>Implement this interface and register as a Spring bean to provide
 * custom subject resolution. Multiple delegates can be registered; they
 * will be chained in {@link Ordered} order by the
 * {@link CompositeSubjectResolver}.
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * @Order(20)
 * public class ApiKeySubjectResolver implements SubjectResolverDelegate {
 *     public String resolve(HttpServletRequest request, OacEntitlementConfig config) {
 *         return request.getHeader("X-Api-Key");
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface SubjectResolverDelegate extends SubjectResolver {
    // Inherits resolve() from SubjectResolver
    // Inherits getOrder() from Ordered
}