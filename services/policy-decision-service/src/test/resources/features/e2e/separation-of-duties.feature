@e2e @SoD @Governance
Feature: Separation of Duties — Policy Author, Approver, and Requester Isolation

  As a compliance officer
  I want to enforce separation of duties between policy creation, approval, and transaction execution
  So that no single principal can control conflicting operations

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  @CriticalPath
  Scenario: Approver cannot approve own transaction
    Given a policy document with effect "ALLOW" and SpEL condition "subject.id != resource.requesterId" is saved to MongoDB
    Given a subject with id "alice" and department "trading"
    And a resource type "transaction" with id "TXN-001"
    And a runtime context with key "requesterId" value "alice"
    And an action "approve"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @CriticalPath
  Scenario: Different requester and approver — ALLOW
    Given a policy document with effect "ALLOW" and SpEL condition "subject.id != resource.requesterId" is saved to MongoDB
    Given a subject with id "bob-manager" and department "trading"
    And a resource type "transaction" with id "TXN-001"
    And a runtime context with key "requesterId" value "alice"
    And an action "approve"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"