@cdp @rebac @boundary-scoped
Feature: Boundary-Scoped ReBAC Relationships
  As a CDP platform architect
  I want ReBAC relationships scoped by boundary dimensions
  So that a manager in retail/cards cannot access wealth-management resources through their retail relationship

  Background:
    Given the policy decision service is running on a random port

  @BoundaryScopedReBAC
  Scenario: Direct relationship with matching boundaryScope is traversed
    Given a relationship edge from "retail-manager" to "party-1" of type "member_of" with boundaryScope market "retail" and lineOfBusiness "cards" is saved to MongoDB
    And a policy document with effect "ALLOW" and name "POL.CDP.PARTY.READ.ALLOW.v1" for action "read" and resource type "party" is saved to MongoDB
    And the policy requires relationship "member_of" with boundaryScope market "retail" and lineOfBusiness "cards"
    And a subject "human" with id "retail-manager"
    And an action "read"
    And a resource type "party" with id "party-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @BoundaryScopedReBAC
  Scenario: Relationship with non-matching boundaryScope is NOT traversed
    Given a relationship edge from "retail-manager" to "party-2" of type "member_of" with boundaryScope market "enterprise" and lineOfBusiness "wealth-management" is saved to MongoDB
    And a policy document with effect "ALLOW" and name "POL.CDP.PARTY.READ.ALLOW.v1" for action "read" and resource type "party" is saved to MongoDB
    And the policy requires relationship "member_of" with boundaryScope market "retail" and lineOfBusiness "cards"
    And a subject "human" with id "retail-manager"
    And an action "read"
    And a resource type "party" with id "party-2"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_REBAC_NO_RELATIONSHIP"

  @BoundaryScopedReBAC
  Scenario: Unscoped relationship is traversed when policy declares no boundaryScope
    Given a relationship edge from "generic-user" to "party-3" of type "member_of" is saved to MongoDB
    And a policy document with effect "ALLOW" and name "POL.CDP.PARTY.READ.ALLOW.v1" for action "read" and resource type "party" is saved to MongoDB
    And the policy requires relationship "member_of"
    And a subject "human" with id "generic-user"
    And an action "read"
    And a resource type "party" with id "party-3"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @BoundaryScopedReBAC
  Scenario: Relationship with partial boundaryScope does not match a policy requiring more dimensions
    Given a relationship edge from "partial-scope-user" to "party-4" of type "member_of" with boundaryScope market "retail" is saved to MongoDB
    And a policy document with effect "ALLOW" and name "POL.CDP.PARTY.READ.ALLOW.v1" for action "read" and resource type "party" is saved to MongoDB
    And the policy requires relationship "member_of" with boundaryScope market "retail" and lineOfBusiness "cards"
    And a subject "human" with id "partial-scope-user"
    And an action "read"
    And a resource type "party" with id "party-4"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_REBAC_NO_RELATIONSHIP"

  @BoundaryScopedReBAC
  Scenario: Relationship chain traverses scoped intermediate hops to the target
    Given a relationship edge from "ceo" to "vp" of type "manages" with boundaryScope market "retail" and lineOfBusiness "cards" is saved to MongoDB
    And a relationship edge from "vp" to "ORD-999" of type "member_of" with boundaryScope market "retail" and lineOfBusiness "cards" is saved to MongoDB
    And a policy document with effect "ALLOW" and name "POL.CDP.ORDER.READ.ALLOW.v1" for action "read" and resource type "order" is saved to MongoDB
    And the policy requires relationship "member_of" with boundaryScope market "retail" and lineOfBusiness "cards"
    And a subject "human" with id "ceo"
    And an action "read"
    And a resource type "order" with id "ORD-999"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @BoundaryScopedReBAC
  Scenario: Policy with declared scope excludes unscoped edges
    Given a relationship edge from "scoped-policy-user" to "party-5" of type "member_of" is saved to MongoDB
    And a policy document with effect "ALLOW" and name "POL.CDP.PARTY.READ.ALLOW.v1" for action "read" and resource type "party" is saved to MongoDB
    And the policy requires relationship "member_of" with boundaryScope market "retail" and lineOfBusiness "cards"
    And a subject "human" with id "scoped-policy-user"
    And an action "read"
    And a resource type "party" with id "party-5"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_REBAC_NO_RELATIONSHIP"
