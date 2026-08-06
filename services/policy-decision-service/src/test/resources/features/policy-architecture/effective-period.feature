@cdp @policy-architecture @effective-period
Feature: Policy Effective Period
  As a policy administrator
  I want policies scoped to calendar validity windows
  So that temporary access grants expire automatically

  Background:
    Given the policy decision service is running on a random port

  @EffectivePeriod
  Scenario: Policy with past effectiveUntil is not evaluated
    Given a policy document with effect "ALLOW" and name "POL.EXPIRED.POLICY.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has effectiveUntil "2020-01-01T00:00:00Z"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"

  @EffectivePeriod
  Scenario: Policy with future effectiveFrom is not evaluated yet
    Given a policy document with effect "ALLOW" and name "POL.SCHEDULED.POLICY.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has effectiveFrom "2099-01-01T00:00:00Z"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"

  @EffectivePeriod
  Scenario: Policy with active effective window is evaluated
    Given a policy document with effect "ALLOW" and name "POL.ACTIVE.WINDOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has effectiveFrom "2020-01-01T00:00:00Z"
    And the policy has effectiveUntil "2099-01-01T00:00:00Z"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @EffectivePeriod
  Scenario: Policy without effective window is always evaluated
    Given a policy document with effect "ALLOW" and name "POL.NO.WINDOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
