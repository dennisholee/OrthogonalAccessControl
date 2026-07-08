package com.oac.enforcement.resolver;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Delegate for JWT parsing using the jjwt (io.jsonwebtoken) library.
 *
 * <p>Supports HMAC (HS256/HS384/HS512) via {@code secret} and
 * asymmetric key verification via {@code jwksUri} for RSA/EC keys.</p>
 *
 * <p>This class is separated to isolate jjwt imports so that
 * {@link JwtSubjectResolver} does not directly depend on jjwt at the
 * import level. If jjwt is not on the classpath, this class will
 * fail to load, and the resolver will simply be unavailable.</p>
 */
public final class JwtParserDelegate {

    private static final Logger log = LoggerFactory.getLogger(JwtParserDelegate.class);

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ConcurrentMap<String, Key> jwksCache = new ConcurrentHashMap<>();

    private JwtParserDelegate() {
        // Utility class
    }

    /**
     * Parse a JWT token and extract the subject claim according to the schema.
     *
     * @param token  the raw JWT string
     * @param schema the JWT schema configuration
     * @return the subject claim value, or null if not found
     */
    public static String parseSubject(String token, JwtSubjectResolver.Schema schema) {
        try {
            var parser = Jwts.parser();

            // Configure signing key — try JWKS URI first, then fall back to secret
            Object signingKey = resolveSigningKey(schema);
            if (signingKey instanceof SecretKey sk) {
                parser.verifyWith(sk);
            } else if (signingKey instanceof PublicKey pk) {
                parser.verifyWith(pk);
            }

            // Require issuer if configured
            if (schema.getIssuer() != null && !schema.getIssuer().isBlank()) {
                parser.requireIssuer(schema.getIssuer());
            }

            JwtParser builtParser = parser.build();

            Jws<Claims> jws = builtParser.parseSignedClaims(token);
            Claims claims = jws.getPayload();

            String subjectId = claims.get(schema.getClaim(), String.class);
            if (subjectId == null || subjectId.isBlank()) {
                log.debug("JWT claim '{}' not found or blank in token", schema.getClaim());
                return null;
            }
            return subjectId;

        } catch (Exception e) {
            log.debug("JWT parsing failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Resolves the signing key for token verification.
     * Priority: JWKS URI (cached) > HMAC secret > null (no verification).
     */
    private static Object resolveSigningKey(JwtSubjectResolver.Schema schema) {
        // Try JWKS URI first
        if (schema.getJwksUri() != null && !schema.getJwksUri().isBlank()) {
            return jwksCache.computeIfAbsent(schema.getJwksUri(), uri -> {
                try {
                    return fetchKeyFromJwksUri(uri);
                } catch (Exception e) {
                    log.warn("Failed to fetch JWKS from {}: {}", uri, e.getMessage());
                    return null;
                }
            });
        }

        // Fall back to HMAC secret
        if (schema.getSecret() != null && !schema.getSecret().isBlank()) {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(schema.getSecret()));
        }

        return null;
    }

    /**
     * Fetches a signing key from a JWKS URI.
     * This is a simplified implementation — production systems should use
     * a proper JWKS client with key rotation, caching headers, and retry.
     */
    private static Key fetchKeyFromJwksUri(String jwksUri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUri))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("JWKS fetch failed: HTTP " + response.statusCode());
        }

        // Parse the JWKS response to extract the first key
        // For production, use a proper JWKS library (e.g., Nimbus JOSE+JWT)
        // This simplified version supports raw PEM X.509 certificates
        String body = response.body();
        if (body.contains("BEGIN CERTIFICATE") || body.contains("BEGIN PUBLIC KEY")) {
            return parsePemKey(body);
        }

        // For JWKS JSON format, log a warning and return null.
        // Production systems should use nimbus-jose-jwt or similar.
        log.warn("JWKS response is in JSON format. For production, consider using nimbus-jose-jwt.");
        return null;
    }

    private static Key parsePemKey(String pem) throws Exception {
        // Strip PEM headers and decode Base64
        String base64 = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(base64);

        // Try X.509 certificate first
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new java.io.ByteArrayInputStream(pem.getBytes()));
            return cert.getPublicKey();
        } catch (Exception e) {
            // Fall back to raw public key
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            return kf.generatePublic(keySpec);
        }
    }
}