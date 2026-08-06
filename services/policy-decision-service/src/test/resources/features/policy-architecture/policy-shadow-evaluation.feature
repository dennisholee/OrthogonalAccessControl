@cdp @policy-architecture @shadow-evaluation
Feature: Policy Shadow Evaluation
  As a policy administrator
  I want DRAFT policies evaluated against live traffic without enforcement
  So that behavioural impact can be assessed before promotion

  Background:
    Given the policy decision service is running on a random port

  @ShadowEval
  Scenario: Shadow policy that would match does not affect the decision
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a shadow evaluation policy with effect "DENY" and name "POL.DRAFT.DENY.ACCOUNT.v1" for action "read" and resource type "account" is saved to MongoDB
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And the shadow-decisions collection should contain 1 entry for policy "POL.DRAFT.DENY.ACCOUNT.v1"

  @ShadowEval
  Scenario: Shadow policy that would allow does not grant access
    Given a policy document with effect "DENY" and name "POL.GLOBAL.DENY.ACCOUNT.v1" for action "read" and resource type "account" is saved to MongoDB
    And a shadow evaluation policy with effect "ALLOW" and name "POL.DRAFT.ALLOW.ACCOUNT.v1" for action "read" and resource type "account" is saved to MongoDB
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"
    And the shadow-decisions collection should contain 1 entry for policy "POL.DRAFT.ALLOW.ACCOUNT.v1"

  @ShadowEval
  Scenario: Shadow policy that does not match produces no shadow entry
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a shadow evaluation policy with effect "DENY" and name "POL.DRAFT.DENY.ORDER.v1" for action "read" and resource type "order" is saved to MongoDB
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And the shadow-decisions collection should contain 0 entries for policy "POL.DRAFT.DENY.ORDER.v1"
