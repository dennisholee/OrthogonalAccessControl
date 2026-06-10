package com.oac.decision.application.service.decision.rules.caveats;

import com.oac.decision.application.service.decision.CaveatEvaluator;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.model.AttributeAccessMap;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Evaluates TIME_WINDOW caveats. A policy with a time window caveat only applies
 * within the specified start/end timestamps (ISO 8601). Supports optional timezone.
 */
public class TimeWindowCaveatEvaluator implements CaveatEvaluator {

    @Override
    public boolean evaluate(DecisionContext context, Map<String, Object> caveatParams) {
        Instant now = Instant.now();

        String startStr = stringParam(caveatParams, "start");
        String endStr = stringParam(caveatParams, "end");

        if (startStr != null) {
            Instant start = Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(startStr));
            if (now.isBefore(start)) {
                return false;
            }
        }

        if (endStr != null) {
            Instant end = Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(endStr));
            if (now.isAfter(end)) {
                return false;
            }
        }

        return true;
    }

    private String stringParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val == null ? null : val.toString();
    }
}