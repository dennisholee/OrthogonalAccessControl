@e2e @ABAC
Feature: ABAC Attribute Conditions — Subject, Resource, and Environment

  As a security architect
  I want to enforce authorization decisions based on subject, resource,
  and environment attributes via SpEL conditions stored in MongoDB policies
  So that fine-grained ABAC policies are correctly evaluated

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  @CriticalPath
  Scenario: Subject department matches policy condition — ALLOW
    Given a policy document with effect "ALLOW" and SpEL condition "subject.department == 'compliance'" is saved to MongoDB
    Given a subject with id "auditor-alice" and department "compliance"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CriticalPath
  Scenario: Subject department mismatch — DENY
    Given a policy document with effect "ALLOW" and SpEL condition "subject.department == 'hr'" is saved to MongoDB
    Given a subject with id "eng-bob" and department "engineering"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"
    And the decision code should be "SPEL_CONDITION_FAILED"

  @Boundary
  Scenario: Environment risk score blocks high-value transaction
    Given a policy document with effect "ALLOW" and SpEL condition "environment.riskScore < 70" is saved to MongoDB
    Given a subject with id "trader" and department "trading"
    And a runtime context with key "riskScore" value "85"
    And an action "execute_trade"
    And a resource type "order" with id "ORD-HIGH"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"
    And the decision code should be "SPEL_CONDITION_FAILED"

  @CriticalPath
  Scenario: Combined ABAC conditions — all must pass for ALLOW
    Given a policy document with effect "ALLOW" and SpEL condition "subject.department == 'hr' && environment.currentHour >= 9 && environment.currentHour < 17" is saved to MongoDB
    Given a subject with id "hr-alice" and department "hr"
    And a runtime context with key "currentHour" value "14"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"