@e2e @Composition @PolicyCondition
Feature: Policy Condition Composition — Multi-Condition AND, Validation, and Backward Compatibility

  As a policy author
  I want to compose policies from typed conditions
  So that policy intent is self-documenting and structurally validated

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  # ====================================================================
  # MULTI-CONDITION AND SEMANTICS
  # ====================================================================

  @CriticalPath
  Scenario: Multiple conditions in a single policy — all pass — ALLOW
    Given a policy spec with effect "ALLOW" and name "POL.COMPOSITE.ALLOW.v1"
    And a condition of type "spel" with expression "subject.department == 'compliance'"
    And a condition of type "timeWindow" with window "09:00-17:00" and timezone "UTC"
    And the policy spec conditions are saved to MongoDB
    Given a subject with id "auditor-alice" and department "compliance"
    And a runtime context with key "currentHour" value "14"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CriticalPath
  Scenario: Multiple conditions in a single policy — one fails — DENY
    Given a policy spec with effect "ALLOW" and name "POL.COMPOSITE.DENY.v1"
    And a condition of type "spel" with expression "subject.department == 'compliance'"
    And a condition of type "timeWindow" with window "09:00-17:00" and timezone "UTC"
    And the policy spec conditions are saved to MongoDB
    Given a subject with id "eng-bob" and department "engineering"
    And a runtime context with key "currentHour" value "14"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  # ====================================================================
  # CONDITION ORDERING — order should not affect evaluation
  # ====================================================================

  @Boundary
  Scenario: Condition ordering does not affect decision outcome
    Given a policy spec with effect "ALLOW" and name "POL.COMPOSITE.ORDER.v1"
    And a condition of type "sourceIp" with cidr "10.0.0.0/8"
    And a condition of type "timeWindow" with window "09:00-17:00" and timezone "UTC"
    And the policy spec conditions are saved to MongoDB
    Given a subject "human" with id "user-reader"
    And a runtime context with key "sourceIp" value "10.0.0.55"
    And a runtime context with key "currentHour" value "14"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  # ====================================================================
  # BACKWARD COMPATIBILITY — old flat format still works
  # ====================================================================

  @CriticalPath
  Scenario: Old flat-format policy document is still accepted and evaluated correctly
    Given a flat-format policy document with effect "ALLOW" and spelCondition "subject.department == 'hr'" is saved to MongoDB
    Given a subject with id "hr-alice" and department "hr"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CriticalPath
  Scenario: New conditions[] format produces same decision as old flat format for equivalent policies
    Given a policy spec with effect "ALLOW" and name "POL.COMPAT.NEW.v1"
    And a condition of type "spel" with expression "subject.department == 'hr'"
    And the policy spec conditions are saved to MongoDB
    Given a subject with id "hr-alice" and department "hr"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  # ====================================================================
  # REPEATED/WILDCARD REBAC CONDITION
  # ====================================================================

  @Boundary
  Scenario: Workload cannot have ReBAC condition — validation at policy create
    Given a policy spec with effect "ALLOW" and name "POL.INVALID.COMBO.v1"
    And a condition of type "rebac" with relationship "manages"
    And the subject type is "workload"
    When the policy spec is submitted for creation via HTTP
    Then the response status should be 400
    And the decision code should be "VALIDATION_ERROR"