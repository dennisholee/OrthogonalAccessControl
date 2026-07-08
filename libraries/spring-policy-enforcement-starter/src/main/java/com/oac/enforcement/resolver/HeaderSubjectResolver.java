package com.oac.enforcement.resolver;

import com.oac.enforcement.OacEntitlementConfig;
import com.oac.enforcement.OacEntitlementProperties.Identity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;

/**
 * Resolves the subject ID from HTTP headers. This is the default resolver
 * strategy and reproduces the original inline header resolution logic from
 * {@link com.oac.enforcement.OacEnforcementInterceptor}.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Per-operation header override ({@link OacEntitlementConfig#subjectIdHeader()})</li>
 *   <li>Configured user header (default: {@code X-User-Id})</li>
 *   <li>Configured service header (default: {@code X-Service-Id})</li>
 * </ol>
 */
@Order(0)
public class HeaderSubjectResolver implements SubjectResolver {

    private final Identity identity;

    public HeaderSubjectResolver(Identity identity) {
        this.identity = identity;
    }

    @Override
    public String resolve(HttpServletRequest request, OacEntitlementConfig config) {
        // Per-operation override takes precedence
        if (config != null && config.subjectIdHeader() != null) {
            String headerValue = request.getHeader(config.subjectIdHeader());
            if (headerValue != null && !headerValue.isBlank()) {
                return headerValue;
            }
        }

        // Default: check user header, then service header
        String userId = request.getHeader(identity.getUserHeader());
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        String serviceId = request.getHeader(identity.getServiceHeader());
        if (serviceId != null && !serviceId.isBlank()) {
            return serviceId;
        }
        return null;
    }

    public Identity getIdentity() {
        return identity;
    }
}