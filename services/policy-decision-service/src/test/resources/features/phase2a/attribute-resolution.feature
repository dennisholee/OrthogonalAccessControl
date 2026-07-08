@e2e @Phase2A @AttributeResolution
Feature: Attribute Resolution Pipeline

  As a policy decision service
  I want to resolve subject, resource, and environment attributes
  So that ABAC policies can evaluate conditions over rich attributes

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  @CriticalPath
  Scenario: Resolve subject department from request
    Given a policy document with effect "ALLOW" and SpEL condition "subject.department == 'hr'" is saved to MongoDB
    Given a subject with id "alice" and department "hr"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CriticalPath
  Scenario: Deny when subject department doesn't match
    Given a policy document with effect "ALLOW" and SpEL condition "subject.department == 'hr'" is saved to MongoDB
    Given a subject with id "bob" and department "engineering"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @Boundary
  Scenario: Environment time-window condition enforces business hours
    Given a policy document with effect "ALLOW" and SpEL condition "environment.hour >= 9 && environment.hour < 17" is saved to MongoDB
    Given a subject with id "alice" and department "hr"
    And a runtime context with key "currentHour" value "14"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Boundary
  Scenario: Deny outside business hours
    Given a policy document with effect "ALLOW" and SpEL condition "environment.hour >= 9 && environment.hour < 17" is saved to MongoDB
    Given a subject with id "alice" and department "hr"
    And a runtime context with key "currentHour" value "22"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"