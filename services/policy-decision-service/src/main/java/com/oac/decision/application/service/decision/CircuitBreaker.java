package com.oac.decision.application.service.decision;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory circuit breaker guarding the policy registry (MongoDB) dependency.
 * <p>
 * After {@link #FAILURE_THRESHOLD} consecutive dependency failures the breaker
 * transitions CLOSED → OPEN. While OPEN, the next request that observes a healthy
 * dependency acts as a half-open probe; a successful probe transitions back to
 * CLOSED, a failed probe re-opens the breaker.
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    static final int FAILURE_THRESHOLD = 3;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public State state() {
        return state.get();
    }

    public synchronized void recordFailure() {
        consecutiveFailures.incrementAndGet();
        if (state.get() == State.HALF_OPEN) {
            state.set(State.OPEN);
        } else if (consecutiveFailures.get() >= FAILURE_THRESHOLD) {
            state.set(State.OPEN);
        }
    }

    public synchronized void recordSuccess() {
        consecutiveFailures.set(0);
        if (state.get() != State.CLOSED) {
            state.set(State.CLOSED);
        }
    }

    public synchronized void reset() {
        state.set(State.CLOSED);
        consecutiveFailures.set(0);
    }
}
