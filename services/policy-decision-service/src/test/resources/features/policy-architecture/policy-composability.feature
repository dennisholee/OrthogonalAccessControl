@cdp @policy-architecture @composability
Feature: Policy Composability
  As a policy administrator
  I want composite policies with logical operators
  So that least-privilege access can be expressed as combinations of base policies

  Background:
    Given the policy decision service is running on a random port

  @Composability
  Scenario: AND composition matches when all referenced policies match
    Given a policy document with effect "ALLOW" and name "POL.RETAIL.ACCESS.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has tenant "tenant-a"
    And a policy document with effect "ALLOW" and name "POL.ANALYTICS.PURPOSE.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has purpose "analytics"
    And a policy document with effect "ALLOW" and name "POL.COMPOSITE.RETAIL_AND_ANALYTICS.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has composition "AND" referencing "POL.RETAIL.ACCESS.v1" and "POL.ANALYTICS.PURPOSE.v1"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff" purpose "analytics"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Composability
  Scenario: AND composition does not match when one referenced policy does not match
    Given a policy document with effect "ALLOW" and name "POL.RETAIL.ACCESS.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has tenant "tenant-a"
    And a policy document with effect "ALLOW" and name "POL.ANALYTICS.PURPOSE.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has purpose "analytics"
    And a policy document with effect "ALLOW" and name "POL.COMPOSITE.RETAIL_AND_ANALYTICS.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has composition "AND" referencing "POL.RETAIL.ACCESS.v1" and "POL.ANALYTICS.PURPOSE.v1"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-b" geography "EU" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"

  @Composability
  Scenario: OR composition matches when at least one referenced policy matches
    Given a policy document with effect "ALLOW" and name "POL.RETAIL.ACCESS.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has tenant "tenant-a"
    And a policy document with effect "ALLOW" and name "POL.ENTERPRISE.ACCESS.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has tenant "tenant-b"
    And a policy document with effect "ALLOW" and name "POL.COMPOSITE.OR.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has composition "OR" referencing "POL.RETAIL.ACCESS.v1" and "POL.ENTERPRISE.ACCESS.v1"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-b" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Composability
  Scenario: NOT composition matches when the referenced policy does not match
    Given a policy document with effect "ALLOW" and name "POL.RETAIL.ACCESS.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has tenant "tenant-a"
    And a policy document with effect "ALLOW" and name "POL.COMPOSITE.NOT.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has composition "NOT" referencing "POL.RETAIL.ACCESS.v1"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-b" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Composability
  Scenario: Missing referenced policy causes composite to not match
    Given a policy document with effect "ALLOW" and name "POL.COMPOSITE.MISSING.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has composition "AND" referencing "POL.NONEXISTENT.v1" and "POL.ALSO.MISSING.v1"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"
