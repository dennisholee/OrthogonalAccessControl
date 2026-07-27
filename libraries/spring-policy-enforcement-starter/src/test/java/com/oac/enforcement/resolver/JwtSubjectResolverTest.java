package com.oac.enforcement.resolver;

import com.oac.enforcement.OacEntitlementConfig;
import com.oac.enforcement.OacEntitlementProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link JwtSubjectResolver}.
 * Verifies JWT parsing with different schemas, claims, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class JwtSubjectResolverTest {

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
            "this-is-a-test-secret-key-that-is-at-least-256-bits-long!!".getBytes());
    private static final String BASE64_SECRET = Base64.getEncoder().encodeToString(SECRET_KEY.getEncoded());

    @Mock
    private HttpServletRequest request;

    private JwtSubjectResolver resolver;
    private Map<String, OacEntitlementProperties.JwtSchema> schemas;

    @BeforeEach
    void setUp() {
        schemas = new LinkedHashMap<>();

        OacEntitlementProperties.JwtSchema defaultSchema = new OacEntitlementProperties.JwtSchema();
        defaultSchema.setClaim("sub");
        defaultSchema.setSecret(BASE64_SECRET);
        schemas.put("default", defaultSchema);

        OacEntitlementProperties.JwtSchema oidcSchema = new OacEntitlementProperties.JwtSchema();
        oidcSchema.setClaim("preferred_username");
        oidcSchema.setSecret(BASE64_SECRET);
        schemas.put("oidc", oidcSchema);

        resolver = new JwtSubjectResolver(schemas);
    }

    private String createToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(SECRET_KEY)
                .compact();
    }

    private String createTokenWithClaim(String claimName, String claimValue) {
        return Jwts.builder()
                .claim(claimName, claimValue)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(SECRET_KEY)
                .compact();
    }

    @Test
    void shouldExtractSubjectFromDefaultClaim() {
        String token = createToken("alice");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        String result = resolver.resolve(request, null);
        assertEquals("alice", result);
    }

    @Test
    void shouldReturnNullWhenNoAuthHeader() {
        when(request.getHeader("Authorization")).thenReturn(null);

        assertNull(resolver.resolve(request, null));
    }

    @Test
    void shouldReturnNullWhenNotBearerToken() {
        when(request.getHeader("Authorization")).thenReturn("Basic token123");

        assertNull(resolver.resolve(request, null));
    }

    @Test
    void shouldReturnNullWhenTokenIsEmpty() {
        when(request.getHeader("Authorization")).thenReturn("Bearer   ");

        assertNull(resolver.resolve(request, null));
    }

    @Test
    void shouldReturnNullWhenTokenIsMalformed() {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");

        assertNull(resolver.resolve(request, null));
    }

    @Test
    void shouldUseSchemaBasedOnSubjectType() {
        String token = createTokenWithClaim("preferred_username", "reporting-service");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        OacEntitlementConfig config = new OacEntitlementConfig(
                "read_aggregate", "order", null, false, "oidc", null);

        String result = resolver.resolve(request, config);
        assertEquals("reporting-service", result);
    }

    @Test
    void shouldFallbackToDefaultSchemaWhenSubjectTypeUnknown() {
        String token = createTokenWithClaim("email", "admin@acme.com");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        OacEntitlementConfig config = new OacEntitlementConfig(
                "read", "order", null, false, "unknown-schema", null);

        // "default" schema uses "sub" claim, which won't find "email"
        assertNull(resolver.resolve(request, config));
    }

    @Test
    void shouldReturnNullWhenClaimNotFoundInToken() {
        String token = createTokenWithClaim("email", "admin@acme.com");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // Default schema expects "sub" claim
        assertNull(resolver.resolve(request, null));
    }

    @Test
    void shouldReturnSchemas() {
        assertNotNull(resolver.getSchemas());
        assertTrue(resolver.getSchemas().containsKey("default"));
        assertTrue(resolver.getSchemas().containsKey("oidc"));
    }

    @Test
    void shouldCreateDefaultSchemaWhenNoneConfigured() {
        JwtSubjectResolver emptyResolver = new JwtSubjectResolver(null);
        assertEquals(1, emptyResolver.getSchemas().size());
        assertTrue(emptyResolver.getSchemas().containsKey("default"));
    }
}