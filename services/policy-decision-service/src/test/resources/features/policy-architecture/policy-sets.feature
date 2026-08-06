@governance @policy-sets
Feature: Policy Sets with Combining Algorithms
  As a policy administrator
  I want to group related policies into sets with explicit combining algorithms
  So that denyOverrides, permitOverrides, firstApplicable and onlyOneApplicable
  semantics are enforced at the set level

  Background:
    Given the policy decision service is running on a random port

  Scenario: denyOverrides set with a DENY constituent denies
    Given a policy document with effect "ALLOW" and name "POL.SET.A.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy document with effect "DENY" and name "POL.SET.A.DENY.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy set with id "set-eu-v1" combining "denyOverrides" and policies "POL.SET.A.ALLOW.v1,POL.SET.A.DENY.v1" is saved to MongoDB
    And a subject "human" with id "set-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPLICIT_DENY"

  Scenario: denyOverrides set with only ALLOW constituents allows
    Given a policy document with effect "ALLOW" and name "POL.SET.B.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy set with id "set-eu-v2" combining "denyOverrides" and policies "POL.SET.B.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "set-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  Scenario: permitOverrides set allows when an ALLOW constituent matches despite a DENY constituent
    Given a policy document with effect "DENY" and name "POL.SET.C.DENY.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy document with effect "ALLOW" and name "POL.SET.C.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy set with id "set-us-v1" combining "permitOverrides" and policies "POL.SET.C.DENY.v1,POL.SET.C.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "set-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  Scenario: firstApplicable set uses the first matching constituent
    Given a policy document with effect "ALLOW" and name "POL.SET.D.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy document with effect "DENY" and name "POL.SET.D.DENY.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy set with id "set-first-v1" combining "firstApplicable" and policies "POL.SET.D.ALLOW.v1,POL.SET.D.DENY.v1" is saved to MongoDB
    And a subject "human" with id "set-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  Scenario: onlyOneApplicable set allows when exactly one constituent matches
    Given a policy document with effect "ALLOW" and name "POL.SET.E.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy set with id "set-only-v1" combining "onlyOneApplicable" and policies "POL.SET.E.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "set-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  Scenario: onlyOneApplicable set denies when multiple constituents match
    Given a policy document with effect "ALLOW" and name "POL.SET.F.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy document with effect "DENY" and name "POL.SET.F.DENY.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy set with id "set-only-v2" combining "onlyOneApplicable" and policies "POL.SET.F.ALLOW.v1,POL.SET.F.DENY.v1" is saved to MongoDB
    And a subject "human" with id "set-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPLICIT_DENY"

  Scenario: canary by-tenant set applies for a targeted tenant
    Given a policy document with effect "ALLOW" and name "POL.SET.G.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy set with id "set-canary-v1" combining "denyOverrides" and policies "POL.SET.G.ALLOW.v1" is saved to MongoDB
    And the policy set "set-canary-v1" has canary by-tenant "tenant-dev"
    And a subject "human" with id "set-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-dev" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  Scenario: canary by-tenant set does not apply for an untargeted tenant
    Given a policy document with effect "ALLOW" and name "POL.SET.H.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy set with id "set-canary-v2" combining "denyOverrides" and policies "POL.SET.H.ALLOW.v1" is saved to MongoDB
    And the policy set "set-canary-v2" has canary by-tenant "tenant-dev"
    And a subject "human" with id "set-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"
  Scenario: environment-scoped set applies only when the request declares the environment
    Given a policy document with effect "ALLOW" and name "POL.SET.I.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy set with id "set-env-v1" combining "denyOverrides" and policies "POL.SET.I.ALLOW.v1" is saved to MongoDB
    And the policy set "set-env-v1" has environment "staging"
    And a subject "human" with id "set-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "environment" value "staging"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  Scenario: environment-scoped set does not apply without a matching environment
    Given a policy document with effect "ALLOW" and name "POL.SET.J.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a policy set with id "set-env-v2" combining "denyOverrides" and policies "POL.SET.J.ALLOW.v1" is saved to MongoDB
    And the policy set "set-env-v2" has environment "staging"
    And a subject "human" with id "set-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"

