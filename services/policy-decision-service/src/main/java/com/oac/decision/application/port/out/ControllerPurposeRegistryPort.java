package com.oac.decision.application.port.out;

/**
 * Output port for the controller purpose registry (Section 4.17).
 * <p>
 * Each tenant that operates as a data controller MUST register its authorised purpose
 * scope in the PAP. The PDP consults this registry to validate that a requested purpose
 * is within the controller's processing instructions.
 */
public interface ControllerPurposeRegistryPort {

    /**
     * Checks whether the given tenant (as data controller) has registered the purpose.
     *
     * @return true when the purpose is authorised for the controller; false when not registered.
     */
    boolean isPurposeAuthorized(String tenant, String purpose);

    /**
     * Registers a purpose for a tenant. Used by the PAP (and tests) to seed the registry.
     */
    void registerPurpose(String tenant, String purpose, String lawfulBasis);
}
