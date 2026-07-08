package com.oac.enforcement.resolver;

import com.oac.enforcement.OacEntitlementProperties;
import com.oac.enforcement.OacEntitlementConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the subject ID from a JWT Bearer token in the Authorization header.
 *
 * <p>Supports multiple named JWT schemas, each with its own claim path.
 * Schema selection is driven by {@link OacEntitlementConfig#subjectType()} —
 * falling back to the {@code "default"} schema when no subject type is specified.</p>
 *
 * <p>This resolver requires jjwt (io.jsonwebtoken) on the classpath.
 * It is conditionally registered when jjwt classes are detected.</p>
 */
@Order(10)
public class JwtSubjectResolver implements SubjectResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtSubjectResolver.class);

    private final Map<String, Schema> schemas;

    public JwtSubjectResolver(Map<String, OacEntitlementProperties.JwtSchema> propertySchemas) {
        this.schemas = new LinkedHashMap<>();
        if (propertySchemas != null) {
            for (Map.Entry<String, OacEntitlementProperties.JwtSchema> entry : propertySchemas.entrySet()) {
                this.schemas.put(entry.getKey(), new Schema(
                        entry.getValue().getClaim(),
                        entry.getValue().getIssuer(),
                        entry.getValue().getJwksUri(),
                        entry.getValue().getSecret()
                ));
            }
        }
        // Ensure at least a default schema exists
        if (!this.schemas.containsKey("default")) {
            this.schemas.put("default", new Schema("sub", null, null, null));
        }
    }

    @Override
    public String resolve(HttpServletRequest request, OacEntitlementConfig config) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }

        // Select schema: use subjectType from config, fall back to "default"
        String schemaName = "default";
        if (config != null && config.subjectType() != null && !config.subjectType().isEmpty()) {
            schemaName = config.subjectType();
        }

        Schema schema = schemas.get(schemaName);
        if (schema == null) {
            log.warn("No JWT schema configured for '{}', falling back to 'default'", schemaName);
            schema = schemas.get("default");
            if (schema == null) {
                log.warn("No 'default' JWT schema configured either");
                return null;
            }
        }

        try {
            return JwtParserDelegate.parseSubject(token, schema);
        } catch (Exception e) {
            log.debug("JWT resolution failed: {}", e.getMessage());
            return null;
        }
    }

    public Map<String, Schema> getSchemas() {
        return schemas;
    }

    /**
     * Internal schema configuration for a single JWT schema.
     */
    public static class Schema {
        private final String claim;
        private final String issuer;
        private final String jwksUri;
        private final String secret;

        public Schema(String claim, String issuer, String jwksUri, String secret) {
            this.claim = claim;
            this.issuer = issuer;
            this.jwksUri = jwksUri;
            this.secret = secret;
        }

        public String getClaim() { return claim; }
        public String getIssuer() { return issuer; }
        public String getJwksUri() { return jwksUri; }
        public String getSecret() { return secret; }
    }
}