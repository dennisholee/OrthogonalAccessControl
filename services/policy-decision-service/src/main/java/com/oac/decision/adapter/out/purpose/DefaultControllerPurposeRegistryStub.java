package com.oac.decision.adapter.out.purpose;

import com.oac.decision.application.port.out.ControllerPurposeRegistryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default stub for ControllerPurposeRegistryPort when MongoDB is not active.
 * Returns true for all purposes (no controller constraint) — used for non-CDP
 * deployments where controller purpose registration is not required.
 */
@Component
@Profile("!mongodb")
public class DefaultControllerPurposeRegistryStub implements ControllerPurposeRegistryPort {

    private final Set<String> registered = new LinkedHashSet<>();

    @Override
    public boolean isPurposeAuthorized(String tenant, String purpose) {
        // When no registrations exist, assume no controller purpose constraint (fail-open).
        // CDP deployments MUST use the MongoDB-backed adapter via the mongodb profile.
        if (registered.isEmpty()) {
            return true;
        }
        return registered.contains(tenant + "::" + purpose);
    }

    @Override
    public void registerPurpose(String tenant, String purpose, String lawfulBasis) {
        registered.add(tenant + "::" + purpose);
    }
}
