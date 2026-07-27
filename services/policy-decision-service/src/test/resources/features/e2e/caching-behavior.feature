@e2e @Caching
Feature: Decision Caching — Cache Hits, TTL, Invalidation, and Staleness

  As a performance engineer
  I want the decision cache to serve repeated authorization checks
  So that MongoDB queries are reduced and latency is minimized

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  # ====================================================================
  # CACHE HIT (Gap 7)
  # ====================================================================

  @CriticalPath
  Scenario: First request caches decision; second request hits cache
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"
    Given MongoDB is stopped
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"

  @Boundary
  Scenario: Cache miss queries MongoDB for fresh decision
    Given a policy document with effect "DENY" and name "POL.GLOBAL.ACCESS.DENY.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "delete"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPLICIT_DENY"

  # ====================================================================
  # TTL EXPIRY (Gap 7)
  # ====================================================================

  @Caching
  Scenario: Cache entry expires after TTL
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"
    When the decision cache TTL is expired
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"

  # ====================================================================
  # CACHE INVALIDATION ON POLICY UPDATE (Gap 7)
  # ====================================================================

  @Caching
  Scenario: Policy update invalidates cache and re-evaluates
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"
    Given a policy document with effect "DENY" and name "POL.RBAC.ACCOUNT.READ.DENY.v1" is saved to MongoDB
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPLICIT_DENY"

  # ====================================================================
  # CACHE KEY SCOPING (Gap 7)
  # ====================================================================

  @Boundary
  Scenario: Cache is scoped by subject + action + resource + boundary
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a policy document with effect "DENY" and name "POL.DENY.EVE.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPLICIT_DENY"
    Given a subject "human" with id "user-reader"
    And a boundary context tenant "tenant-b" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPLICIT_DENY"

  # ====================================================================
  # STALENESS WINDOW (Gap 7)
  # ====================================================================

  @Caching
  Scenario: Eventual consistency mode serves cached decision within staleness window
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"
    And the response should include header "X-OAC-Cache" with value "miss"
    Given a policy document with effect "DENY" and name "POL.RBAC.ACCOUNT.READ.DENY.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"
    And the response should include header "X-OAC-Cache" with value "hit"