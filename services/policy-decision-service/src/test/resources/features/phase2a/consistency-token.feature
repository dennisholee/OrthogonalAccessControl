@e2e @Phase2A @ConsistencyToken
Feature: Consistency Token Enforcement

  As a security platform
  I want to enforce causal consistency via consistency tokens
  So that stale reads after policy or relationship mutations are prevented

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  @CriticalPath
  Scenario: Request with valid consistency token succeeds
    Given a policy document with effect "ALLOW" is saved to MongoDB
    And a consistency token "token-001" is the latest for policy updates
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CriticalPath
  Scenario: Request with stale consistency token is denied
    Given a policy document with effect "ALLOW" is saved to MongoDB
    And the latest consistency token is "token-002"
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent with token "token-001"
    Then the response status should be 200
    And the decision should be "DENY"
    And the decision code should be "CONSISTENCY_VIOLATION"

  @CriticalPath
  Scenario: Request without consistency token uses eventual consistency mode
    Given a policy document with effect "ALLOW" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Boundary
  Scenario: Strict consistency request with stale token returns error detail
    Given a policy document with effect "ALLOW" is saved to MongoDB
    And a consistency token "token-003" is the latest for policy updates
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request with strict consistency flag And token "token-001" is sent
    Then the response status should be 200
    And the decision should be "DENY"
    And the decision code should be "CONSISTENCY_VIOLATION"
    And the explanation should contain "required token"

  @Boundary
  Scenario: Token with unknown subject is allowed with warning
    Given a consistency token "token-999" has never been issued
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent with token "token-999"
    Then the response status should be 200
    And the decision should be "ALLOW"

  @ErrorHandling
  Scenario: Missing path between user and resource returns clear error
    Given a user "alice" and resource "ORD-999" have no relationship
    And a policy document with effect "ALLOW" is saved to MongoDB
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    And an action "approve"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"
    And the decision code should be "DECISION_REBAC_NO_RELATIONSHIP"
