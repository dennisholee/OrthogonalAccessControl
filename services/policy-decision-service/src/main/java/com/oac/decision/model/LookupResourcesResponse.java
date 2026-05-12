package com.oac.decision.model;

import java.util.List;

public record LookupResourcesResponse(
        List<String> resourceIds,
        String nextPageToken
) {
}
