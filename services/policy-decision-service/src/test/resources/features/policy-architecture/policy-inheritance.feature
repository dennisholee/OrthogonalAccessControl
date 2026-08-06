@cdp @policy-architecture @inheritance
Feature: Policy Inheritance
  As a policy administrator
  I want child policies to inherit scope and effect from parent policies
  So that domain refinement and default-to-override patterns are supported

  Background:
    Given the policy decision service is running on a random port

  @Inheritance
  Scenario: Child policy inherits parent's boundary scope
    Given a policy document with effect "ALLOW" and name "POL.EU.BASE.ACCESS.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a policy document with effect "ALLOW" and name "POL.RETAIL.EU.ACCESS.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy inheritsFrom "POL.EU.BASE.ACCESS.v1"
    And a subject "human" with id "eu-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Inheritance
  Scenario: Child policy overrides inherited tenant scope
    Given a policy document with effect "ALLOW" and name "POL.EU.BASE.ACCESS.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a policy document with effect "ALLOW" and name "POL.RETAIL.EU.ACCESS.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy inheritsFrom "POL.EU.BASE.ACCESS.v1"
    And the policy overrides tenant "tenant-b"
    And a subject "human" with id "eu-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-b" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Inheritance
  Scenario: Parent policy remains independently effective for its own scope
    Given a policy document with effect "ALLOW" and name "POL.EU.BASE.ACCESS.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a policy document with effect "ALLOW" and name "POL.RETAIL.EU.ACCESS.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy inheritsFrom "POL.EU.BASE.ACCESS.v1"
    And the policy overrides tenant "tenant-b"
    And a subject "human" with id "eu-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Inheritance
  Scenario: Child policy inherits parent's DENY effect
    Given a policy document with effect "DENY" and name "POL.GLOBAL.DELETE.DENY.v1" for action "delete" and resource type "order" is saved to MongoDB
    And the policy has tenant "tenant-a"
    And a policy document with effect "ALLOW" and name "POL.RETAIL.DELETE.v1" for action "delete" and resource type "order" is saved to MongoDB
    And the policy inheritsFrom "POL.GLOBAL.DELETE.DENY.v1"
    And a subject "human" with id "operator"
    And an action "delete"
    And a resource type "order" with id "ORD-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPLICIT_DENY"

  @Inheritance
  Scenario: Missing parent policy resolves to standalone child
    Given a policy document with effect "ALLOW" and name "POL.ORPHAN.CHILD.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy inheritsFrom "POL.NONEXISTENT.PARENT.v1"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Inheritance
  Scenario: Inheritance chain of depth 3 resolves correctly
    Given a policy document with effect "ALLOW" and name "POL.LEVEL1.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has tenant "tenant-a"
    And a policy document with effect "ALLOW" and name "POL.LEVEL2.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy inheritsFrom "POL.LEVEL1.v1"
    And the policy overrides geography "EU"
    And a policy document with effect "ALLOW" and name "POL.LEVEL3.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy inheritsFrom "POL.LEVEL2.v1"
    And the policy overrides market "enterprise"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "enterprise" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
