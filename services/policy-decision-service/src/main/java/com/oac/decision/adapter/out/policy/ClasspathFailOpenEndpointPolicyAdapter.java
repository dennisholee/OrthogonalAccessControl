package com.oac.decision.adapter.out.policy;

import com.oac.decision.application.port.out.FailOpenEndpointPolicyPort;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClasspathFailOpenEndpointPolicyAdapter implements FailOpenEndpointPolicyPort {

    private static final String ENDPOINTS_RESOURCE = "fail-open-endpoints.properties";

    private final Set<String> approvedEndpointKeys;

    public ClasspathFailOpenEndpointPolicyAdapter() {
        this(ENDPOINTS_RESOURCE);
    }

    ClasspathFailOpenEndpointPolicyAdapter(String resourceName) {
        this.approvedEndpointKeys = loadApprovedEndpoints(resourceName);
    }

    @Override
    public boolean isFailOpenApproved(String endpointKey) {
        return endpointKey != null && approvedEndpointKeys.contains(endpointKey);
    }

    private Set<String> loadApprovedEndpoints(String resourceName) {
        Properties properties = new Properties();
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                return Set.of();
            }
            properties.load(inputStream);
        } catch (IOException exception) {
            return Set.of();
        }

        String configured = properties.getProperty("approved", "");
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(this::isValidEndpointKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean isValidEndpointKey(String entry) {
        if (entry.isBlank()) {
            return false;
        }
        int separatorIndex = entry.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex == entry.length() - 1) {
            return false;
        }
        return entry.indexOf(':', separatorIndex + 1) == -1;
    }
}
