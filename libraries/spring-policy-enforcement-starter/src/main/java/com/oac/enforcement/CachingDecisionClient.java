package com.oac.enforcement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Objects;

/**
 * Decorator for {@link DecisionClient} that caches PDP decisions using
 * Spring's Cache Abstraction with Caffeine.
 *
 * Cache keys are composed of subjectId + "::" + action + "::" + resourceId.
 * Cache entries are automatically evicted based on TTL configured in
 * {@code spring.cache.caffeine.spec}.
 */
public class CachingDecisionClient implements DecisionClient {

    private static final Logger log = LoggerFactory.getLogger(CachingDecisionClient.class);

    static final String CACHE_NAME = "oac.decisions";

    private final DecisionClient delegate;
    private final Cache cache;

    public CachingDecisionClient(DecisionClient delegate, CacheManager cacheManager) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.cache = Objects.requireNonNull(cacheManager, "cacheManager must not be null")
                .getCache(CACHE_NAME);
        if (this.cache == null) {
            throw new IllegalArgumentException(
                    "Cache '" + CACHE_NAME + "' not configured. "
                            + "Add 'spring.cache.cache-names=oac.decisions' and "
                            + "'spring.cache.type=caffeine' to application.yml.");
        }
    }

    @Override
    public boolean checkPermission(String subjectId, String action, String resourceId) {
        String cacheKey = cacheKey(subjectId, action, resourceId);

        Boolean cached = cache.get(cacheKey, Boolean.class);
        if (cached != null) {
            log.trace("OAC cache HIT for key={}", cacheKey);
            return cached;
        }

        boolean result = delegate.checkPermission(subjectId, action, resourceId);
        cache.put(cacheKey, result);

        log.trace("OAC cache MISS for key={}, result={}", cacheKey, result);
        return result;
    }

    /**
     * Evict a specific decision from the cache.
     * Useful when a policy or relationship change is known to affect a subject.
     */
    public void evict(String subjectId, String action, String resourceId) {
        cache.evict(cacheKey(subjectId, action, resourceId));
    }

    /**
     * Clear the entire decision cache (e.g., on policy bundle update).
     */
    public void clear() {
        cache.clear();
        log.info("OAC decision cache cleared");
    }

    private static String cacheKey(String subjectId, String action, String resourceId) {
        return subjectId + "::" + action + "::" + resourceId;
    }
}