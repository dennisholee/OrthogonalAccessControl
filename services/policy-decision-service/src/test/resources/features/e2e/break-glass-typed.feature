@e2e @BreakGlass @cdp
Feature: Break-Glass Emergency Access — Typed, Time-Bounded
  As a break-glass operator
  I want break-glass access to require an explicit BREAK_GLASS policy type
  So that emergency elevation is governed, audited, and time-bounded

  Background:
    Given the policy decision service is running on a random port

  @CriticalPath @BreakGlassTyped
  Scenario: Break-glass policy with active flag grants access
    Given a policy document with effect "ALLOW" and name "POL.BREAK.GLASS.ADMIN.v1" for action "delete" and resource type "order" is saved to MongoDB
    And the policy has policyType "BREAK_GLASS"
    And the policy has effectiveWindow from now minus 1 hours to now plus 23 hours
    And a subject "human" with id "break-glass-operator"
    And an action "delete"
    And a resource type "order" with id "ORD-CRITICAL"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    And a runtime context with key "breakGlassActive" value "true"
    And a runtime context with key "breakGlassReason" value "Critical production incident — P1 severity"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CriticalPath @BreakGlassTyped
  Scenario: Break-glass policy without active flag returns DENY
    Given a policy document with effect "ALLOW" and name "POL.BREAK.GLASS.ADMIN.v1" for action "delete" and resource type "order" is saved to MongoDB
    And the policy has policyType "BREAK_GLASS"
    And the policy has effectiveWindow from now minus 1 hours to now plus 23 hours
    And a subject "human" with id "break-glass-operator"
    And an action "delete"
    And a resource type "order" with id "ORD-CRITICAL"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @CriticalPath @BreakGlassTyped
  Scenario: Expired break-glass policy does not grant access even with active flag
    Given a policy document with effect "ALLOW" and name "POL.BREAK.GLASS.EXPIRED.v1" for action "delete" and resource type "order" is saved to MongoDB
    And the policy has policyType "BREAK_GLASS"
    And the policy has effectiveUntil "2020-01-01T00:00:00Z"
    And a subject "human" with id "break-glass-operator"
    And an action "delete"
    And a resource type "order" with id "ORD-CRITICAL"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    And a runtime context with key "breakGlassActive" value "true"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"

  @CriticalPath @BreakGlassTyped
  Scenario: STANDARD policy does not grant access via break-glass flag
    Given a policy document with effect "ALLOW" and name "POL.STANDARD.READ.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has policyType "STANDARD"
    And a subject "human" with id "break-glass-operator"
    And an action "delete"
    And a resource type "order" with id "ORD-CRITICAL"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    And a runtime context with key "breakGlassActive" value "true"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"
