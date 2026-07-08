package com.oac.enforcement.resolver;

import com.oac.enforcement.OacEntitlementConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;

/**
 * Strategy interface for resolving the subject identity from an HTTP request.
 * Implementations extract a subject ID from headers, JWT claims,
 * or any other identity source.
 *
 * <p>Implementations should also implement {@link Ordered} to control
 * their priority in the resolution chain. Lower order = higher priority.</p>
 */
@FunctionalInterface
public interface SubjectResolver extends Ordered {

    /**
     * Resolve the subject ID from the request, or return null if
     * this resolver cannot determine the subject.
     *
     * @param request the incoming HTTP request
     * @param config  per-operation entitlement config (may be null)
     * @return resolved subject ID, or null if unresolvable
     */
    String resolve(HttpServletRequest request, OacEntitlementConfig config);

    @Override
    default int getOrder() {
        return 0;
    }
}