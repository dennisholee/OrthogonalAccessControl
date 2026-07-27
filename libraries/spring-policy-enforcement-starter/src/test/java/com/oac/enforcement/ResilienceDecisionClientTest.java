package com.oac.enforcement;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link ResilienceDecisionClient}.
 * Verifies circuit breaker state transitions, retry on failure, fallback behavior.
 */
@ExtendWith(MockitoExtension.class)
class ResilienceDecisionClientTest {

    @Mock
    private DecisionClient delegate;

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private ResilienceDecisionClient client;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());

        retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(Exception.class)
                .build());

        client = new ResilienceDecisionClient(delegate, circuitBreakerRegistry, retryRegistry);
    }

    @Test
    void shouldReturnResultWhenDelegateSucceeds() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001")).thenReturn(true);

        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));
        verify(delegate, times(1)).checkPermission("alice", "read", "order/ORD-001");
    }

    @Test
    void shouldReturnFalseWhenDelegateReturnsDeny() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001")).thenReturn(false);

        assertFalse(client.checkPermission("alice", "read", "order/ORD-001"));
        verify(delegate, times(1)).checkPermission("alice", "read", "order/ORD-001");
    }

    @Test
    void shouldRetryOnTransientFailure() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001"))
                .thenThrow(new RuntimeException("Timeout"))
                .thenReturn(true);

        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));
        verify(delegate, times(2)).checkPermission("alice", "read", "order/ORD-001");
    }

    @Test
    void shouldReturnFalseAfterRetriesExhausted() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001"))
                .thenThrow(new RuntimeException("Persistent failure"));

        assertFalse(client.checkPermission("alice", "read", "order/ORD-001"));
        verify(delegate, times(2)).checkPermission("alice", "read", "order/ORD-001"); // 2 retry attempts
    }

    @Test
    void circuitBreakerShouldOpenAfterThreshold() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001"))
                .thenThrow(new RuntimeException("Failure"));

        // First two calls fail (with retries: each call triggers 2 attempts = 4 total fails)
        // Circuit opens after minimumNumberOfCalls=2 with >50% failure rate
        assertFalse(client.checkPermission("alice", "read", "order/ORD-001"));
        assertFalse(client.checkPermission("alice", "read", "order/ORD-001"));

        CircuitBreaker circuitBreaker = client.getCircuitBreaker();
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void shouldReturnCircuitBreakerAndRetryInstances() {
        assertNotNull(client.getCircuitBreaker());
        assertNotNull(client.getRetry());
        assertEquals(CircuitBreaker.State.CLOSED, client.getCircuitBreaker().getState());
    }

    @Test
    void shouldCreateWithDefaultConfig() {
        ResilienceDecisionClient defaultClient = new ResilienceDecisionClient(delegate);
        assertNotNull(defaultClient.getCircuitBreaker());
        assertNotNull(defaultClient.getRetry());
    }

    @Test
    void shouldReturnFalseWhenDelegateThrowsRuntimeException() {
        when(delegate.checkPermission("alice", "read", "order/ORD-001"))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertFalse(client.checkPermission("alice", "read", "order/ORD-001"));
    }
}