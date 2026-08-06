@e2e @BreakGlass @cdp @effective-period
Feature: Break-Glass 24-Hour Time Bound
  As a platform security administrator
  I want break-glass policies limited to a 24-hour activation window
  So that emergency elevation cannot be provisioned for extended periods

  Background:
    Given the policy decision service is running on a random port

  @CriticalPath @BreakGlassLimit
  Scenario: Break-glass policy with 24-hour window grants access when active
    Given a policy document with effect "ALLOW" and name "POL.BREAK.GLASS.SHORT.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has policyType "BREAK_GLASS"
    And the policy has effectiveWindow from now minus 1 hours to now plus 23 hours
    And a subject "human" with id "incident-responder"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "breakGlassActive" value "true"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CriticalPath @BreakGlassLimit
  Scenario: Break-glass policy with 48-hour window is not eligible even when active
    Given a policy document with effect "ALLOW" and name "POL.BREAK.GLASS.LONG.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has policyType "BREAK_GLASS"
    And the policy has effectiveWindow from now minus 1 hours to now plus 47 hours
    And a subject "human" with id "incident-responder"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "breakGlassActive" value "true"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"

  @CriticalPath @BreakGlassLimit
  Scenario: Non-break-glass policy with long effective window is unaffected
    Given a policy document with effect "ALLOW" and name "POL.STANDARD.LONG.WINDOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has policyType "STANDARD"
    And the policy has effectiveWindow from now minus 1 hours to now plus 1000 hours
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
