package com.oac.example.bdd;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Factory for creating test JWT tokens for BDD scenarios.
 * Uses a fixed shared secret for deterministic signing.
 */
public class JwtTokenFactory {

    private final SecretKey signingKey;
    private final String encodedSecret;

    /**
     * Create a JWT token factory with the given Base64-encoded secret.
     */
    public JwtTokenFactory(String base64Secret) {
        this.encodedSecret = base64Secret;
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
    }

    /**
     * Create a signed JWT token with the given claims.
     *
     * @param claims the claims to include in the payload
     * @return a signed JWT string
     */
    public String createToken(Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000)) // 1 hour
                .signWith(signingKey)
                .compact();
    }

    /**
     * Create a signed JWT token with a single string claim.
     *
     * @param claimName  the claim name (e.g., "sub")
     * @param claimValue the claim value
     * @return a signed JWT string
     */
    public String createToken(String claimName, String claimValue) {
        return createToken(Map.of(claimName, claimValue));
    }

    /**
     * Create a signed JWT token with multiple claims.
     *
     * @param claimValue the value for the "sub" claim
     * @param additionalClaims additional claims to include
     * @return a signed JWT string
     */
    public String createTokenWithClaims(String claimValue, Map<String, Object> additionalClaims) {
        var claims = new java.util.LinkedHashMap<String, Object>();
        claims.put("sub", claimValue);
        claims.putAll(additionalClaims);
        return createToken(claims);
    }

    /**
     * Get the Base64-encoded secret for use in test configuration.
     */
    public String getEncodedSecret() {
        return encodedSecret;
    }

    /**
     * Create a default factory for BDD tests using a fixed test secret.
     * The 256-bit key is derived deterministically: Base64("test-secret-key-for-bdd-tests!!!").
     * "test-secret-key-for-bdd-tests!!!" is exactly 32 bytes = 256 bits.
     */
    public static JwtTokenFactory defaultFactory() {
        String rawKey = "test-secret-key-for-bdd-tests!!!";
        String base64Secret = Base64.getEncoder().encodeToString(rawKey.getBytes());
        return new JwtTokenFactory(base64Secret);
    }

    /**
     * Get the Base64-encoded 256-bit secret from the default factory.
     * Use this value to configure test properties.
     */
    public static String getDefaultBase64Secret() {
        String rawKey = "test-secret-key-for-bdd-tests!!!";
        return Base64.getEncoder().encodeToString(rawKey.getBytes());
    }
}