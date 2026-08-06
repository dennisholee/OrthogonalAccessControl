@e2e @BreakGlass
Feature: Break-Glass Emergency Access — Time-Bounded Override

  As a break-glass operator
  I want to activate temporary elevated access with mandatory approval and expiry
  So that emergency operations can proceed while preserving audit compliance

  Background:
    Given the policy decision service is running on a random port

  @CriticalPath
  Scenario: Break-glass activation grants temporary access
    Given a policy document with effect "ALLOW" and name "POL.BREAK.GLASS.ADMIN.v1" for action "delete" and resource type "order" is saved to MongoDB
    And the policy has policyType "BREAK_GLASS"
    And the policy has effectiveWindow from now minus 1 hours to now plus 23 hours
    Given a subject "human" with id "break-glass-operator"
    And an action "delete"
    And a resource type "order" with id "ORD-CRITICAL"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    And a runtime context with key "breakGlassActive" value "true"
    And a runtime context with key "breakGlassReason" value "Critical production incident — P1 severity"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CriticalPath
  Scenario: Break-glass without active flag returns DENY
    Given a policy document with effect "ALLOW" and name "POL.BREAK.GLASS.ADMIN.v1" for action "delete" and resource type "order" is saved to MongoDB
    And the policy has policyType "BREAK_GLASS"
    And the policy has effectiveWindow from now minus 1 hours to now plus 23 hours
    Given a subject "human" with id "break-glass-operator"
    And an action "delete"
    And a resource type "order" with id "ORD-CRITICAL"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"