@cdp @policy-architecture @certification
Feature: Policy Certification
  As a policy administrator
  I want certification due dates enforced on matching policies
  So that stale policies are flagged before they silently drift out of governance

  Background:
    Given the policy decision service is running on a random port

  @Certification
  Scenario: Matching policy past next certification date emits a WARN audit event
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy "POL.RBAC.ACCOUNT.READ.ALLOW.v1" has a certification nextCertificationDate "2020-01-01" lastCertifiedBy "policy-owner-1" lastCertifiedAt "2019-12-01"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And an audit event of type "POLICY_CERTIFICATION_EXPIRED" should exist with severity "WARN" for entity "POL.RBAC.ACCOUNT.READ.ALLOW.v1"

  @Certification
  Scenario: Certification waiver with future expiry suppresses the WARN
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy "POL.RBAC.ACCOUNT.READ.ALLOW.v1" has a certification nextCertificationDate "2020-01-01" lastCertifiedBy "policy-owner-1" lastCertifiedAt "2019-12-01" with a waiver expiring "2099-01-01"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And no audit event of type "POLICY_CERTIFICATION_EXPIRED" should exist for entity "POL.RBAC.ACCOUNT.READ.ALLOW.v1"

  @Certification
  Scenario: Policy not yet due for certification produces no WARN
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy "POL.RBAC.ACCOUNT.READ.ALLOW.v1" has a certification nextCertificationDate "2099-01-01" lastCertifiedBy "policy-owner-1" lastCertifiedAt "2026-01-01"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And no audit event of type "POLICY_CERTIFICATION_EXPIRED" should exist for entity "POL.RBAC.ACCOUNT.READ.ALLOW.v1"

  @Certification
  Scenario: RESTRICTED policy is not evaluated by the decision engine
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is in state "RESTRICTED"
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"
    And no audit event of type "POLICY_CERTIFICATION_EXPIRED" should exist for entity "POL.RBAC.ACCOUNT.READ.ALLOW.v1"
