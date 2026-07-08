package com.oac.enforcement;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Decorator for {@link DecisionClient} that adds Resilience4j circuit breaker
 * and retry behavior around PDP calls.
 *
 * Circuit breaker prevents cascading failures when the PDP is unavailable.
 * Retry provides resilience against transient failures.
 */
public class ResilienceDecisionClient implements DecisionClient {

    private static final Logger log = LoggerFactory.getLogger(ResilienceDecisionClient.class);

    private static final String CIRCUIT_BREAKER_NAME = "oacPdp";
    private static final String RETRY_NAME = "oacPdpRetry";

    private final DecisionClient delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    /**
     * Creates a ResilienceDecisionClient with default Resilience4j configuration.
     *
     * @param delegate the underlying DecisionClient to decorate
     */
    public ResilienceDecisionClient(DecisionClient delegate) {
        this(delegate, null, null);
    }

    /**
     * Creates a ResilienceDecisionClient with the given registries (or defaults).
     *
     * @param delegate          the underlying DecisionClient to decorate
     * @param circuitRegistry   optional circuit breaker registry; defaults to a 5-count sliding window
     * @param retryRegistry     optional retry registry; defaults to 3 retries with 100ms backoff
     */
    public ResilienceDecisionClient(DecisionClient delegate,
                                     CircuitBreakerRegistry circuitRegistry,
                                     RetryRegistry retryRegistry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");

        CircuitBreakerRegistry cbRegistry = circuitRegistry != null
                ? circuitRegistry
                : CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build());

        this.circuitBreaker = cbRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("OAC circuit breaker state transition: {} -> {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()));

        RetryRegistry rRegistry = retryRegistry != null
                ? retryRegistry
                : RetryRegistry.of(RetryConfig.custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofMillis(100))
                        .retryExceptions(Exception.class)
                        .ignoreExceptions(IllegalArgumentException.class)
                        .build());

        this.retry = rRegistry.retry(RETRY_NAME);
    }

    @Override
    public boolean checkPermission(String subjectId, String action, String resourceId) {
        // Build the retry-wrapped supplier first, then wrap it with the circuit breaker
        Supplier<Boolean> retryable = Retry.decorateSupplier(retry,
                () -> delegate.checkPermission(subjectId, action, resourceId));
        Supplier<Boolean> decorated = CircuitBreaker.decorateSupplier(circuitBreaker, retryable);

        try {
            return decorated.get();
        } catch (Exception e) {
            log.error("OAC PDP call failed after circuit breaker + retry: {} {} {}",
                    subjectId, action, resourceId, e);
            // On circuit breaker open or retry exhaustion, return false (deny closed)
            return false;
        }
    }

    /**
     * Returns the underlying CircuitBreaker for monitoring.
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Returns the underlying Retry for monitoring.
     */
    public Retry getRetry() {
        return retry;
    }
}