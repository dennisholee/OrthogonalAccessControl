@e2e @Resilience @Failover
Feature: Resilience — Dependency Outage, Fail-Open, Circuit Breaker, and Fallback

  As a platform reliability engineer
  I want the decision service to handle dependency outages gracefully
  So that fail-open endpoints remain available and fail-closed endpoints fail safe

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  # ====================================================================
  # FAIL-OPEN BEHAVIOR (Gap 5)
  # ====================================================================

  @Resilience
  Scenario: Fail-open endpoint returns ALLOW during MongoDB outage
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And MongoDB is stopped
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And an endpoint classification "FAIL_OPEN"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Resilience
  Scenario: Fail-open endpoint during outage logs fallback header
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And MongoDB is stopped
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And an endpoint classification "FAIL_OPEN"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And the response should include header "X-OAC-Fallback" with value "true"

  # ====================================================================
  # CIRCUIT BREAKER (Gap 6)
  # ====================================================================

  @Resilience
  Scenario: Circuit breaker opens after threshold failures
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And MongoDB is stopped
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEPENDENCY_UNAVAILABLE"
    When the same check permission request is repeated 4 times
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEPENDENCY_UNAVAILABLE"
    And the response should include header "X-OAC-Circuit-Breaker" with value "open"

  @Resilience
  Scenario: Circuit breaker half-open probe succeeds and recovers on next request
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And MongoDB is stopped
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When 5 check permission requests are sent via HTTP to open circuit breaker
    And response should include circuit breaker "open"
    And MongoDB is started
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And the response should include header "X-OAC-Circuit-Breaker" with value "half-open"

  # ====================================================================
  # FALLBACK TO NoOpDecisionClient (Gap 6)
  # ====================================================================

  @Resilience
  Scenario: Complete fallback to configurable default decision when no recovery path
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And MongoDB is stopped
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-0"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEPENDENCY_UNAVAILABLE"

  # ====================================================================
  # HEALTH INDICATOR (Gap 6)
  # ====================================================================

  @Resilience
  Scenario: Health indicator reports DEGRADED during partial outage
    Given MongoDB is stopped
    When the health endpoint is queried
    Then the response status should be 200
    And the health response should include component "oacDecision" with status "DEGRADED"

  @Boundary
  Scenario: Health indicator reports UP when all dependencies healthy
    Given MongoDB is seeded with baseline fixtures
    When the health endpoint is queried
    Then the response status should be 200
    And the health response should include component "oacDecision" with status "UP"