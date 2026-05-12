@feature-driven
Feature: Policy decision behaviors

  Scenario: Default deny when no policies match
    Given fixture-backed policy matching is used
    And no matched policies
    And subject "human" with id "user-1"
    And action "read"
    And resource type "account" with id "acc-1"
    And boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When check permission is evaluated
    Then decision should be "DENY" with code "DECISION_DEFAULT_DENY"

  Scenario: Allow when an ALLOW policy is matched
    Given fixture-backed policy matching is used
    And matched policies are
      | policy                           |
      | POL.RETAIL.ACCOUNT.VIEW.ALLOW.v1 |
    And subject "human" with id "user-2"
    And action "view"
    And resource type "account" with id "acc-2"
    And boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And runtime context entries are
      | key                    | value    |
      | resourceTenant         | tenant-a |
      | resourceGeography      | us       |
      | resourceMarket         | retail   |
      | resourceLineOfBusiness | cards    |
      | resourceChannel        | staff    |
    When check permission is evaluated
    Then decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"

  Scenario: Explicit deny policy overrides allow policy
    Given fixture-backed policy matching is used
    And matched policies are
      | policy                          |
      | POL.RBAC.ACCOUNT.READ.ALLOW.v1 |
      | POL.GLOBAL.ACCESS.DENY.v1      |
    And subject "human" with id "user-reader"
    And action "read"
    And resource type "account" with id "acc-3"
    And boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When check permission is evaluated
    Then decision should be "DENY" with code "DECISION_EXPLICIT_DENY"

  Scenario: PBAC-style approval policy allows in in-memory registry
    Given in-memory policy registry is used
    And subject "human" with id "user-9"
    And action "approve"
    And resource type "account" with id "acc-9"
    And boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And runtime context entries are
      | key           | value |
      | approvalLevel | L1    |
      | resourceTenant         | tenant-a |
      | resourceGeography      | us       |
      | resourceMarket         | retail   |
      | resourceLineOfBusiness | cards    |
      | resourceChannel        | staff    |
    When check permission is evaluated
    Then decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"
    And matched policies should contain "POL.PBAC.APPROVAL.L1.ALLOW.v1"

  Scenario: Boundary mismatch denies even when allow policy is matched
    Given fixture-backed policy matching is used
    And matched policies are
      | policy                          |
      | POL.RBAC.ACCOUNT.READ.ALLOW.v1 |
    And subject "human" with id "user-reader"
    And action "read"
    And resource type "account" with id "acc-4"
    And boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And runtime context entries are
      | key                    | value     |
      | resourceTenant         | tenant-a  |
      | resourceGeography      | us        |
      | resourceMarket         | corporate |
      | resourceLineOfBusiness | cards     |
      | resourceChannel        | staff     |
    When check permission is evaluated
    Then decision should be "DENY" with code "DECISION_BOUNDARY_DENY"

  Scenario: Strict consistency path denies when consistency token is missing
    Given fixture-backed policy matching is used
    And matched policies are
      | policy                          |
      | POL.RBAC.ACCOUNT.READ.ALLOW.v1 |
    And subject "human" with id "user-reader"
    And action "read"
    And resource type "account" with id "acc-5"
    And boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And strict consistency is enabled
    And runtime context entries are
      | key                    | value    |
      | resourceTenant         | tenant-a |
      | resourceGeography      | us       |
      | resourceMarket         | retail   |
      | resourceLineOfBusiness | cards    |
      | resourceChannel        | staff    |
      | requiredConsistencyToken | token-123 |
    When check permission is evaluated
    Then decision should be "DENY" with code "DECISION_CONSISTENCY_TOKEN_REQUIRED"
