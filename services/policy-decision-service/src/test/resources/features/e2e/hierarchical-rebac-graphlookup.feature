@e2e @ReBAC @GraphLookup
Feature: Hierarchical ReBAC via MongoDB $graphLookup — Organization Chart Traversal

  As a security architect
  I want to enforce authorization based on hierarchical organization relationships
  So that managers at any level can access resources owned by their reports

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  @CriticalPath
  Scenario: 3-hop organization chain allows top-level manager access to leaf resource
    Given a relationship chain "CEO->VP->Director->CSR:manages"
    And a relationship edge from "CSR" to "ORD-001" of type "manages" is saved to MongoDB
    Given a policy document with effect "ALLOW" requiring relationship "manages" is saved to MongoDB
    Given a subject "human" with id "CEO"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Boundary
  Scenario: Direct report has manages edge to parent — bidirectional
    Given a relationship edge from "CSR" to "ORD-001" of type "manages" is saved to MongoDB
    Given a policy document with effect "ALLOW" requiring relationship "manages" is saved to MongoDB
    Given a subject "human" with id "CSR"
    And an action "read"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Boundary
  Scenario: Multi-role relationship chain resolves correctly
    Given a relationship chain "alice->ORD-001:approver"
    And a policy document with effect "ALLOW" requiring relationship "manages" is saved to MongoDB
    Given a subject "human" with id "alice"
    And an action "approve"
    And a resource type "order" with id "ORD-001"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"
