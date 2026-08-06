@cdp @policy-architecture @consistency
Feature: Consistency Token Partitioning
  As a platform architect
  I want consistency tokens partitioned by scope
  So that a change in one scope does not invalidate caches for unrelated scopes

  Background:
    Given the policy decision service is running on a random port

  @TokenPartition
  Scenario: Matching GLOBAL scope token passes
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a fresh consistency token is issued for scope "GLOBAL"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And the request uses the issued token for scope "GLOBAL"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @TokenPartition
  Scenario: Stale TENANT scope token denies
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a fresh consistency token is issued for scope "TENANT::tenant-a"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a consistency token "stale-token" for scope "TENANT::tenant-a"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "CONSISTENCY_VIOLATION"

  @TokenPartition
  Scenario: Mixed token vector with one stale scope denies
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a fresh consistency token is issued for scope "GLOBAL"
    And a fresh consistency token is issued for scope "TENANT::tenant-a"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And the request uses the issued token for scope "GLOBAL"
    And a consistency token "stale-token" for scope "TENANT::tenant-a"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "CONSISTENCY_VIOLATION"

  @TokenPartition
  Scenario: Full token vector with all scopes matching passes
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a fresh consistency token is issued for scope "GLOBAL"
    And a fresh consistency token is issued for scope "TENANT::tenant-a"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And the request uses the issued token for scope "GLOBAL"
    And the request uses the issued token for scope "TENANT::tenant-a"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @TokenPartition
  Scenario: Response includes consistency token vector for involved scopes
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a fresh consistency token is issued for scope "GLOBAL"
    And a fresh consistency token is issued for scope "TENANT::tenant-a"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And the response should include consistency tokens for scopes "GLOBAL" and "TENANT::tenant-a"

