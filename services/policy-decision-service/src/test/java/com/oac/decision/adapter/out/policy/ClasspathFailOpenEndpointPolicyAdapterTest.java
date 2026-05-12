package com.oac.decision.adapter.out.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathFailOpenEndpointPolicyAdapterTest {

    @Test
    void knownEndpointIsApprovedFromExternalizedRegistry() {
        ClasspathFailOpenEndpointPolicyAdapter adapter = new ClasspathFailOpenEndpointPolicyAdapter();

        assertThat(adapter.isFailOpenApproved("account:read")).isTrue();
    }

    @Test
    void unknownEndpointIsDeniedFromExternalizedRegistry() {
        ClasspathFailOpenEndpointPolicyAdapter adapter = new ClasspathFailOpenEndpointPolicyAdapter();

        assertThat(adapter.isFailOpenApproved("payment:read")).isFalse();
    }

    @Test
    void missingRegistryResourceDeniesAllEndpoints() {
        ClasspathFailOpenEndpointPolicyAdapter adapter =
                new ClasspathFailOpenEndpointPolicyAdapter("fail-open-endpoints-missing.properties");

        assertThat(adapter.isFailOpenApproved("account:read")).isFalse();
        assertThat(adapter.isFailOpenApproved("statement:read")).isFalse();
    }

    @Test
    void malformedRegistryEntriesAreIgnored() {
        ClasspathFailOpenEndpointPolicyAdapter adapter =
                new ClasspathFailOpenEndpointPolicyAdapter("fail-open-endpoints-malformed.properties");

        assertThat(adapter.isFailOpenApproved("account:read")).isTrue();
        assertThat(adapter.isFailOpenApproved("badentry")).isFalse();
        assertThat(adapter.isFailOpenApproved("too:many:parts")).isFalse();
    }
}
