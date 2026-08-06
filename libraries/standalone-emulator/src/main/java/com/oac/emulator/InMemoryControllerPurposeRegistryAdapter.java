package com.oac.emulator;

import com.oac.decision.application.port.out.ControllerPurposeRegistryPort;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * In-memory controller purpose registry for the standalone emulator.
 * Matches the {@code MongoControllerPurposeRegistryAdapter} contract: a purpose is
 * authorised for a tenant when registered. Fail-open when no registrations exist
 * (non-CDP deployments).
 */
public class InMemoryControllerPurposeRegistryAdapter implements ControllerPurposeRegistryPort {

    private final Set<String> registered = new LinkedHashSet<>();

    @Override
    public boolean isPurposeAuthorized(String tenant, String purpose) {
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
