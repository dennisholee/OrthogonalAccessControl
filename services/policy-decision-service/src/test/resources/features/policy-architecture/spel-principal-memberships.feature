@cdp @spel-bindings @domain-membership
Feature: Principal Memberships in SpEL Conditions
  As a policy author
  I want to condition policies on principal domain memberships
  So that domain-scoped policies can reference the principal's authorised domains

  Background:
    Given the policy decision service is running on a random port

  @MembershipSpel
  Scenario: Policy references principalMemberships.tenants
    Given a policy document with effect "ALLOW" and name "POL.CDP.MULTI_TENANT.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "principalMemberships.tenants.contains('tenant-a')"
    And a subject "human" with id "multi-tenant-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And principal domain membership tenants "tenant-a,tenant-b" markets "retail" geographies "EU" linesOfBusiness "cards" channels "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @MembershipSpel
  Scenario: Policy denies when principalMemberships lacks the required tenant
    Given a policy document with effect "ALLOW" and name "POL.CDP.MULTI_TENANT.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "principalMemberships.tenants.contains('tenant-a')"
    And a subject "human" with id "single-tenant-user"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-b" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And principal domain membership tenants "tenant-b" markets "retail" geographies "EU" linesOfBusiness "cards" channels "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "SPEL_CONDITION_FAILED"

  @MembershipSpel
  Scenario: Policy references principalMemberships.purposes
    Given a policy document with effect "ALLOW" and name "POL.CDP.PURPOSE.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "principalMemberships.purposes.contains('marketing-campaign')"
    And a subject "human" with id "marketing-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And principal domain membership tenants "tenant-a" markets "retail" geographies "EU" linesOfBusiness "cards" channels "staff" purposes "marketing-campaign,analytics"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @MembershipSpel
  Scenario: Policy references principalMemberships.regulatoryRegimes
    Given a policy document with effect "ALLOW" and name "POL.CDP.REGIME.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "principalMemberships.regulatoryRegimes.contains('GDPR')"
    And a subject "human" with id "eu-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And principal domain membership tenants "tenant-a" markets "retail" geographies "EU" linesOfBusiness "cards" channels "staff" regulatoryRegimes "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @MembershipSpel
  Scenario: Principal memberships absent from request does not NPE in SpEL
    Given a policy document with effect "ALLOW" and name "POL.CDP.EMPTY_MEMBERSHIP.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "principalMemberships.tenants.isEmpty()"
    And a subject "human" with id "no-membership-user"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
