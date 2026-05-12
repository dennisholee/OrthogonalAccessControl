package com.oac.decision.model;

public record ErrorItem(
        String code,
        String message,
        boolean retryable
) {
}
