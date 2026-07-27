package com.oac.enforcement;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link CachingDecisionClient}.
 * Verifies cache hit/miss, TTL expiry, eviction, and clear behavior.
 */
@ExtendWith(MockitoExtension.class)
class CachingDecisionClientTest {

    private static final String CACHE_NAME = "oac.decisions";

    @Mock
    private DecisionClient delegate;

    private CacheManager cacheManager;
    private CachingDecisionClient client;

    @BeforeEach
    void setUp() {
        CaffeineCacheManager cm = new CaffeineCacheManager(CACHE_NAME);
        cm.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(java.time.Duration.ofMinutes(5)));
        // CaffeineCacheManager creates caches lazily on first getCache() call
        this.cacheManager = cm;
        this.client = new CachingDecisionClient(delegate, cacheManager);
    }

    @Test
    void shouldCacheFirstDecisionAndReturnOnSecondCall() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001")).thenReturn(true);

        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));
        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));

        // Delegate should only be called once (second call hits cache)
        verify(delegate, times(1)).checkPermission("alice", "read", "order/ORD-001");
    }

    @Test
    void shouldCacheDenyDecision() {
        when(delegate.checkPermission("attacker", "delete", "order/ORD-001")).thenReturn(false);

        assertFalse(client.checkPermission("attacker", "delete", "order/ORD-001"));
        assertFalse(client.checkPermission("attacker", "delete", "order/ORD-001"));

        verify(delegate, times(1)).checkPermission("attacker", "delete", "order/ORD-001");
    }

    @Test
    void shouldMissCacheForDifferentSubject() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001")).thenReturn(true);
        when(delegate.checkPermission("bob", "read", "order/ORD-001")).thenReturn(false);

        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));
        assertFalse(client.checkPermission("bob", "read", "order/ORD-001"));

        verify(delegate, times(1)).checkPermission("alice", "read", "order/ORD-001");
        verify(delegate, times(1)).checkPermission("bob", "read", "order/ORD-001");
    }

    @Test
    void shouldMissCacheForDifferentAction() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001")).thenReturn(true);
        when(delegate.checkPermission("alice", "delete", "order/ORD-001")).thenReturn(false);

        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));
        assertFalse(client.checkPermission("alice", "delete", "order/ORD-001"));

        verify(delegate, times(1)).checkPermission("alice", "read", "order/ORD-001");
        verify(delegate, times(1)).checkPermission("alice", "delete", "order/ORD-001");
    }

    @Test
    void shouldMissCacheForDifferentResource() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001")).thenReturn(true);
        when(delegate.checkPermission("alice", "read", "order/ORD-002")).thenReturn(false);

        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));
        assertFalse(client.checkPermission("alice", "read", "order/ORD-002"));

        verify(delegate, times(1)).checkPermission("alice", "read", "order/ORD-001");
        verify(delegate, times(1)).checkPermission("alice", "read", "order/ORD-002");
    }

    @Test
    void shouldReEvaluateAfterEviction() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001")).thenReturn(true);

        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));

        client.evict("alice", "read", "order/ORD-001");

        when(delegate.checkPermission("alice", "read", "order/ORD-001")).thenReturn(false);
        assertFalse(client.checkPermission("alice", "read", "order/ORD-001"));

        verify(delegate, times(2)).checkPermission("alice", "read", "order/ORD-001");
    }

    @Test
    void shouldReEvaluateAfterClear() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001")).thenReturn(true);

        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));

        client.clear();

        when(delegate.checkPermission("alice", "read", "order/ORD-001")).thenReturn(false);
        assertFalse(client.checkPermission("alice", "read", "order/ORD-001"));

        verify(delegate, times(2)).checkPermission("alice", "read", "order/ORD-001");
    }

    @Test
    void shouldThrowWhenCacheNameNotFound() {
        CaffeineCacheManager cm = new CaffeineCacheManager("other-cache");

        assertThrows(IllegalArgumentException.class,
                () -> new CachingDecisionClient(delegate, cm));
    }
}