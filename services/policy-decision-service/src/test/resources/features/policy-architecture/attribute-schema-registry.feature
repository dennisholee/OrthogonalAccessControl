@governance @attribute-schema
Feature: Attribute Schema Registry
  As a policy administrator
  I want policy conditions validated against a registered attribute catalog
  So that unknown attribute references are rejected at submission and required
  attributes are enforced at evaluation time

  Background:
    Given the policy decision service is running on a random port

  Scenario: Unknown attribute reference is rejected at policy submission
    Given a policy spec with effect "ALLOW" and name "POL.SCHEMA.UNKNOWN.v1"
    And a condition of type "spel" with expression "subject.nonexistentAttribute == 'x'"
    When the policy spec is submitted for creation via HTTP
    Then the response status should be 400
    And the decision code should be "VALIDATION_ERROR"

  Scenario: Known attribute reference is accepted at policy submission
    Given a policy spec with effect "ALLOW" and name "POL.SCHEMA.KNOWN.v1"
    And a condition of type "spel" with expression "subject.department == 'compliance'"
    When the policy spec is submitted for creation via HTTP
    Then the response status should be 201

  Scenario: Map-root attribute reference is accepted at policy submission
    Given a policy spec with effect "ALLOW" and name "POL.SCHEMA.MAPROOT.v1"
    And a condition of type "spel" with expression "resource.suppressionFlags['LITIGATION_HOLD'] != true"
    When the policy spec is submitted for creation via HTTP
    Then the response status should be 201

  Scenario: Required attribute missing at evaluation denies with DECISION_MISSING_ATTRIBUTE
    Given the attribute schema entry "resource.dataSubjectCategory" of type "ENUM" is registered and required
    And a policy document with effect "ALLOW" and name "POL.SCHEMA.CATEGORY.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "resource.dataSubjectCategory == 'ADULT'"
    And a subject "human" with id "schema-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_MISSING_ATTRIBUTE"

  Scenario: Required attribute present at evaluation allows
    Given the attribute schema entry "resource.dataSubjectCategory" of type "ENUM" is registered and required
    And a policy document with effect "ALLOW" and name "POL.SCHEMA.CATEGORY.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "resource.dataSubjectCategory == 'ADULT'"
    And a subject "human" with id "schema-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceDataSubjectCategory" value "ADULT"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
