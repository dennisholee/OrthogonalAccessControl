@e2e @Phase2A @ReBAC @Hierarchical
Feature: Hierarchical ReBAC via MongoDB $graphLookup

  As a policy decision service
  I want to traverse hierarchical relationships via MongoDB $graphLookup
  So that transitive permissions (e.g., manager-of-manager) are correctly evaluated

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  @CriticalPath
  Scenario: Direct relationship allows access
    Given a policy document with effect "ALLOW" requiring relationship "manages" is saved to MongoDB
    And a relationship edge from "alice" to "ORD-001" of type "manages" is saved to MongoDB
    Given a subject "human" with id "alice"
    And an action "approve"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CriticalPath
  Scenario: One-hop indirect relationship via chain allows access
    Given a policy document with effect "ALLOW" requiring relationship "manages" is saved to MongoDB
    And a relationship chain "alice->bob->charlie:manages"
    And a relationship edge from "charlie" to "ORD-001" of type "manages" is saved to MongoDB
    Given a subject "human" with id "alice"
    And an action "approve"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Boundary
  Scenario: No relationship path results in deny
    Given a policy document with effect "ALLOW" requiring relationship "manages" is saved to MongoDB
    Given a subject "human" with id "unknown"
    And an action "approve"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @Temporal
  Scenario: Expired relationship is treated as no relationship
    Given a policy document with effect "ALLOW" requiring relationship "manages" is saved to MongoDB
    And a relationship edge from "alice" to "ORD-001" of type "manages" with expiry "2020-01-01T00:00:00Z" is saved to MongoDB
    Given a subject "human" with id "alice"
    And an action "approve"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"