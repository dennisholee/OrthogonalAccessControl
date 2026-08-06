@scoping @multi-resource @environment
Feature: Multi-Resource and Environment Policy Scoping
  As a policy administrator
  I want policies to scope to multiple resource types and to specific deployment environments
  So that cross-resource entitlements are expressed without duplication and
  non-production requests are isolated from production data policies

  Background:
    Given the policy decision service is running on a random port

  Scenario: Multi-resource policy matches a declared resource type
    Given a policy document with effect "ALLOW" and name "POL.MULTI.PARTY_HOUSEHOLD.ALLOW.v1" for action "read" and resource type "party" is saved to MongoDB
    And the policy has resourceTypes "party,household"
    And a subject "human" with id "party-analyst"
    And an action "read"
    And a resource type "household" with id "hh-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  Scenario: Multi-resource policy does not match an undeclared resource type
    Given a policy document with effect "ALLOW" and name "POL.MULTI.PARTY_HOUSEHOLD.ALLOW.v1" for action "read" and resource type "party" is saved to MongoDB
    And the policy has resourceTypes "party,household"
    And a subject "human" with id "party-analyst"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"

  Scenario: Environment-scoped policy matches only the declared environment
    Given a policy document with effect "ALLOW" and name "POL.ENV.PRODUCTION.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has environment "production"
    And a subject "human" with id "env-user"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "environment" value "production"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  Scenario: Environment-scoped policy does not match a different environment
    Given a policy document with effect "ALLOW" and name "POL.ENV.PRODUCTION.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has environment "production"
    And a subject "human" with id "env-user"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "environment" value "staging"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"

  Scenario: Policy without an environment scope matches any environment
    Given a policy document with effect "ALLOW" and name "POL.ENV.UNSCOPED.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "env-user"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "environment" value "staging"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
