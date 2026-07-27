@e2e @Governance @PolicyLifecycle
Feature: Policy Lifecycle Governance — Maker-Checker, SoD, Full Lifecycle, and Rollback

  As a compliance officer
  I want to enforce governance controls on policy lifecycle operations
  So that maker-checker separation, state machine constraints, and auditability are maintained

  Background:
    Given the policy decision service is running on a random port

  # ====================================================================
  # MAKER-CHECKER (Gap 1): Policy author cannot self-approve
  # Already covered in policy-decision.feature SECTION 7 @Governance
  # This is an additional explicit scenario for the denial path
  # ====================================================================

  @CriticalPath
  Scenario: Policy author blocked from self-approving promotion
    Given a create policy request for effect "ALLOW" and name "POL.GOV.SELF-APPROVE.v1" with author "self-approver"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent by principal "self-approver" for the created policy to state "ACTIVE"
    Then the response status should be 409
    And the decision code should be "GOVERNANCE_CONFLICT"

  # ====================================================================
  # SEPARATION OF DUTIES (Gap 2): Non-owner cannot promote policy
  # Already covered in policy-decision.feature SECTION 7 @Governance
  # ====================================================================

  @CriticalPath
  Scenario: Non-owner blocked from promoting someone else's policy
    Given a create policy request for effect "ALLOW" and name "POL.GOV.NON-OWNER.v1" with author "owner-user"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent by principal "unauthorized-attacker" for the created policy to state "ACTIVE"
    Then the response status should be 403
    And the decision code should be "GOVERNANCE_SOD_VIOLATION"

  # ====================================================================
  # FULL LIFECYCLE (Gap 3): DRAFT→VALIDATED→APPROVED→ACTIVE chain
  # ====================================================================

  @CriticalPath
  Scenario: Full lifecycle chain — DRAFT promoted to VALIDATED
    Given a create policy request for effect "ALLOW" and name "POL.GOV.LIFECYCLE.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "VALIDATED"
    Then the response status should be 200
    And the policy state should be "VALIDATED"

  @Boundary
  Scenario: Full lifecycle chain — VALIDATED promoted to APPROVED
    Given a create policy request for effect "ALLOW" and name "POL.GOV.LIFECYCLE.v2"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "VALIDATED"
    Then the response status should be 200
    And the policy state should be "VALIDATED"
    When a promote policy request is sent for the created policy to state "APPROVED"
    Then the response status should be 200
    And the policy state should be "APPROVED"

  @CriticalPath
  Scenario: Full lifecycle chain — APPROVED promoted to ACTIVE
    Given a create policy request for effect "ALLOW" and name "POL.GOV.LIFECYCLE.v3"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "VALIDATED"
    Then the response status should be 200
    And the policy state should be "VALIDATED"
    When a promote policy request is sent for the created policy to state "APPROVED"
    Then the response status should be 200
    And the policy state should be "APPROVED"
    When a promote policy request is sent for the created policy to state "ACTIVE"
    Then the response status should be 200
    And the policy state should be "ACTIVE"

  @Boundary
  Scenario: Invalid state transition — DRAFT→APPROVED (skip VALIDATED) rejected
    Given a create policy request for effect "ALLOW" and name "POL.GOV.SKIP.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "APPROVED"
    Then the response status should be 409
    And the decision code should be "POLICY_INVALID_STATE_TRANSITION"

  # ====================================================================
  # ARCHIVAL/ROLLBACK (Gap 4): ACTIVE→ARCHIVED→restore
  # ====================================================================

  @Boundary
  Scenario: Active policy archived to ARCHIVED state
    Given a create policy request for effect "ALLOW" and name "POL.GOV.ARCHIVE.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "ACTIVE"
    Then the response status should be 200
    And the policy state should be "ACTIVE"
    When a promote policy request is sent for the created policy to state "ARCHIVED"
    Then the response status should be 200
    And the policy state should be "ARCHIVED"

  @Boundary
  Scenario: Archived policy restored back to ACTIVE
    Given a create policy request for effect "ALLOW" and name "POL.GOV.RESTORE.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "ACTIVE"
    Then the response status should be 200
    And the policy state should be "ACTIVE"
    When a promote policy request is sent for the created policy to state "ARCHIVED"
    Then the response status should be 200
    And the policy state should be "ARCHIVED"
    When a promote policy request is sent for the created policy to state "ACTIVE"
    Then the response status should be 200
    And the policy state should be "ACTIVE"

  @Boundary
  Scenario: Invalid transition — ARCHIVED→RETIRED from archive rejected
    Given a create policy request for effect "ALLOW" and name "POL.GOV.INVALID-ARCHIVE.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "ACTIVE"
    Then the response status should be 200
    And the policy state should be "ACTIVE"
    When a promote policy request is sent for the created policy to state "ARCHIVED"
    Then the response status should be 200
    And the policy state should be "ARCHIVED"
    When a promote policy request is sent for the created policy to state "RETIRED"
    Then the response status should be 409
    And the decision code should be "POLICY_INVALID_STATE_TRANSITION"