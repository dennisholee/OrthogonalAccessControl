package com.oac.decision.application.service.decision;

import com.oac.decision.model.CheckPermissionResponse;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-process decision cache keyed by the stable identity of a
 * check-permission request (subject + action + resource + boundary).
 * <p>
 * Only decisions computed while the policy registry dependency is healthy are
 * cached. Entries expire after {@link #TTL} to bound staleness, and the whole
 * cache can be invalidated on policy updates.
 */
public class DecisionCache {

    static final Duration TTL = Duration.ofMinutes(5);

    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

    public CheckPermissionResponse get(String key) {
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.evaluatedAt().plus(TTL).isBefore(OffsetDateTime.now())) {
            entries.remove(key);
            return null;
        }
        return entry.response();
    }

    public void put(String key, CheckPermissionResponse response) {
        entries.put(key, new CacheEntry(response, OffsetDateTime.now()));
    }

    public void evictAll() {
        entries.clear();
    }

    public void reset() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    private record CacheEntry(CheckPermissionResponse response, OffsetDateTime evaluatedAt) {
    }
}
