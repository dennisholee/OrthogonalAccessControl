package com.oac.enforcement;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the OAC entitlement enforcement.
 *
 * Configured via application.yml:
 * <pre>
 * oac:
 *   enforcement:
 *     contract-paths:
 *       - classpath:order-service-api.yaml
 *     pdp-url: http://pdp:8080
 *     identity:
 *       resolver-mode: header         # header | jwt | composite
 *       user-header: X-User-Id
 *       service-header: X-Service-Id
 *       jwt:
 *         schemas:
 *           default:
 *             claim: sub
 *             secret: base64-encoded-secret
 *     fail-closed: true
 * </pre>
 */
@ConfigurationProperties(prefix = "oac.enforcement")
public class OacEntitlementProperties {

    /** OpenAPI contract YAML files to parse for x-oac-entitlement vendor extensions */
    private List<String> contractPaths = new ArrayList<>(List.of("classpath:api.yaml"));

    /** PDP base URL for CheckPermission calls */
    private String pdpUrl = "http://localhost:8080";

    /** If true, missing/unparseable contracts fail startup. If false, start without enforcement */
    private boolean failClosed = true;

    /** Identity and subject resolution configuration */
    private Identity identity = new Identity();

    public List<String> getContractPaths() { return contractPaths; }
    public void setContractPaths(List<String> contractPaths) { this.contractPaths = contractPaths; }

    public String getPdpUrl() { return pdpUrl; }
    public void setPdpUrl(String pdpUrl) { this.pdpUrl = pdpUrl; }

    public boolean isFailClosed() { return failClosed; }
    public void setFailClosed(boolean failClosed) { this.failClosed = failClosed; }

    public Identity getIdentity() { return identity; }
    public void setIdentity(Identity identity) { this.identity = identity; }

    /**
     * Identity header and subject resolution configuration.
     * <p>
     * The {@code resolver-mode} property controls which
     * {@link com.oac.enforcement.resolver.SubjectResolver} strategy is used:
     * <ul>
     *   <li>{@code header} — resolve from HTTP headers (default, backward-compatible)</li>
     *   <li>{@code jwt} — resolve from JWT Bearer token claims</li>
     *   <li>{@code composite} — chain all available resolvers in {@code @Order} sequence</li>
     * </ul>
     */
    public static class Identity {
        /** Subject resolver strategy mode: header (default), jwt, or composite */
        private String resolverMode = "header";
        private String userHeader = "X-User-Id";
        private String serviceHeader = "X-Service-Id";
        private JwtConfig jwt = new JwtConfig();

        public String getResolverMode() { return resolverMode; }
        public void setResolverMode(String resolverMode) { this.resolverMode = resolverMode; }

        public String getUserHeader() { return userHeader; }
        public void setUserHeader(String userHeader) { this.userHeader = userHeader; }

        public String getServiceHeader() { return serviceHeader; }
        public void setServiceHeader(String serviceHeader) { this.serviceHeader = serviceHeader; }

        public JwtConfig getJwt() { return jwt; }
        public void setJwt(JwtConfig jwt) { this.jwt = jwt; }
    }

    /**
     * JWT resolver configuration.
     * Supports multiple named schemas, keyed by schema name (e.g., "default", "oidc").
     * Schema selection is driven by {@link com.oac.enforcement.OacEntitlementConfig#subjectType()}.
     */
    public static class JwtConfig {
        private Map<String, JwtSchema> schemas = new LinkedHashMap<>();

        public Map<String, JwtSchema> getSchemas() { return schemas; }
        public void setSchemas(Map<String, JwtSchema> schemas) { this.schemas = schemas; }
    }

    /**
     * A single JWT schema definition.
     */
    public static class JwtSchema {
        /** Claim path to extract the subject ID from (default: "sub") */
        private String claim = "sub";
        /** Optional: require this issuer in the JWT */
        private String issuer;
        /** Optional: JWKS URI for key resolution */
        private String jwksUri;
        /** Optional: Base64-encoded HMAC shared secret (for HS256/HS384/HS512) */
        private String secret;

        public String getClaim() { return claim; }
        public void setClaim(String claim) { this.claim = claim; }

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }

        public String getJwksUri() { return jwksUri; }
        public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }
}
