package com.oac.decision.application.port.out;

import java.util.Optional;

/**
 * Output port for consistency token storage and retrieval.
 *
 * <p>Consistency tokens provide causal consistency guarantees:
 * after a policy or relationship mutation produces a token T,
 * subsequent CheckPermission requests with token T are guaranteed
 * to reflect that mutation.</p>
 *
 * <p>Backed by MongoDB for single-region or CockroachDB/PostgreSQL
 * for multi-region deployments.</p>
 */
public interface ConsistencyTokenStore {

    /**
     * Returns the latest consistency token for the given scope.
     *
     * @param scope a namespace for the token (e.g., "policies", "relationships:acme")
     * @return the latest token, or empty if never issued
     */
    Optional<String> getLatestToken(String scope);

    /**
     * Issues a new consistency token for the given scope.
     * Tokens are monotonically increasing (based on timestamp + sequence).
     *
     * @param scope the namespace for the token
     * @return the newly issued token string
     */
    String issueToken(String scope);

    /**
     * Validates that a request token is not stale.
     *
     * @param scope        the namespace for the token
     * @param requestToken the token provided in the CheckPermission request
     * @return true if the token is current or no token has been issued yet
     */
    default boolean isTokenCurrent(String scope, String requestToken) {
        if (requestToken == null || requestToken.isBlank()) return false;
        return getLatestToken(scope)
                .map(latest -> latest.equals(requestToken))
                .orElse(true);
    }
}