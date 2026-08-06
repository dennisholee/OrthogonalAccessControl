@cdp @policy-architecture
Feature: Cross-Boundary Rule Evaluation
  As a security architect
  I want cross-boundary access to require explicit justification
  So that silent cross-tenant and cross-geography access is prevented

  Background:
    Given the policy decision service is running on a random port

  @CrossBoundary
  Scenario: Cross-tenant access with invalid justification is denied
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a cross-boundary justification "invalid"
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceTenant" value "tenant-b"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CROSS_BOUNDARY_NO_JUSTIFICATION"

  @CrossBoundary
  Scenario: Cross-tenant access with valid justification passes to downstream rules
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a cross-boundary justification "Approved cross-tenant audit for Q3 financial review"
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceTenant" value "tenant-b"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @CrossBoundary
  Scenario: Cross-geography access with invalid justification is denied
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a cross-boundary justification "bad"
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceGeography" value "EU"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CROSS_BOUNDARY_NO_JUSTIFICATION"

  @CrossBoundary
  Scenario: Same-boundary access with justification does not trigger cross-boundary rule
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a cross-boundary justification "Valid justification for same-boundary access"
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"

  @CrossBoundary
  Scenario: Cross-market access with invalid justification is denied
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a cross-boundary justification "nope"
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceMarket" value "enterprise"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CROSS_BOUNDARY_NO_JUSTIFICATION"

  @CrossBoundary @DomainMembership
  Scenario: Cross-tenant access allowed when principal belongs to both domains with valid justification
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a cross-boundary justification "Approved cross-tenant audit for Q3 financial review"
    And a subject "human" with id "multi-tenant-auditor"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceTenant" value "tenant-b"
    And a runtime context with key "resourceGeography" value "us"
    And a runtime context with key "resourceMarket" value "retail"
    And a runtime context with key "resourceLineOfBusiness" value "cards"
    And a runtime context with key "resourceChannel" value "staff"
    And principal domain membership tenants "tenant-a,tenant-b" markets "retail" geographies "us" linesOfBusiness "cards" channels "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CrossBoundary @DomainMembership
  Scenario: Cross-tenant access denied when principal lacks resource domain membership
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a cross-boundary justification "Approved cross-tenant audit for Q3 financial review"
    And a subject "human" with id "tenant-a-only-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceTenant" value "tenant-b"
    And a runtime context with key "resourceGeography" value "us"
    And a runtime context with key "resourceMarket" value "retail"
    And a runtime context with key "resourceLineOfBusiness" value "cards"
    And a runtime context with key "resourceChannel" value "staff"
    And principal domain membership tenants "tenant-a" markets "retail" geographies "us" linesOfBusiness "cards" channels "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DOMAIN_NOT_IN_SCOPE"