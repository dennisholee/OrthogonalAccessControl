package com.oac.decision.model;

import java.util.List;

public record ErrorResponse(
        String decisionCode,
        List<ErrorItem> errors
) {
}
