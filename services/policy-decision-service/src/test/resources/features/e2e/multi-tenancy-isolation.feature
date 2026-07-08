@e2e @MultiTenancy
Feature: Multi-Tenancy & Boundary Isolation

  As a platform security administrator
  I want to enforce tenant, geography, line-of-business, and channel boundaries
  So that cross-tenant data access is prevented by default

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  @CriticalPath
  Scenario: Tenant boundary — admin cannot access resources in different tenant
    Given a policy document with effect "ALLOW" and name "POL.TENANT.ACME.ALLOW.v1" is saved to MongoDB
    Given a subject "human" with id "tenant-admin"
    And an action "read"
    And a resource type "account" with id "acc-tenant-b"
    And a boundary context tenant "tenant-b" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @CriticalPath
  Scenario: Geography mismatch — UK user cannot access EU data
    Given a policy document with effect "ALLOW" and name "POL.GEO.UK.ALLOW.v1" is saved to MongoDB
    Given a subject "human" with id "uk-manager"
    And an action "read"
    And a resource type "account" with id "acc-eu"
    And a boundary context tenant "acme" geography "UK" market "enterprise" lineOfBusiness "retail" channel "staff"
    And a runtime context with key "resourceGeography" value "EU"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @Boundary
  Scenario: Line-of-business boundary — retail user cannot access wealth data
    Given a policy document with effect "ALLOW" and name "POL.LOB.RETAIL.ALLOW.v1" is saved to MongoDB
    Given a subject "human" with id "retail-csr"
    And an action "read"
    And a resource type "account" with id "acc-wealth"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "wealth-management" channel "staff"
    And a runtime context with key "resourceLineOfBusiness" value "wealth-management"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @CriticalPath
  Scenario: Correct tenant scoping allows access
    Given a policy document with effect "ALLOW" and name "POL.TENANT.ACME.ALLOW.v1" is saved to MongoDB
    Given a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Boundary
  Scenario: Cross-geography explicit allow with auditable justification
    Given a policy document with effect "ALLOW" and name "POL.CROSS.GEO.EXPLICIT.v1" is saved to MongoDB
    Given a subject "human" with id "cross-geo-auditor"
    And an action "audit"
    And a resource type "account" with id "acc-eu"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    And a runtime context with key "resourceGeography" value "EU"
    And a runtime context with key "crossGeoJustification" value "Sarbanes-Oxley audit Q3 2026"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @ErrorHandling
  Scenario: Missing tenant in boundary context returns validation error
    Given a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "" geography "" market "" lineOfBusiness "" channel ""
    When a check permission request is sent via HTTP
    Then the response status should be 400

  @Boundary
  Scenario: Staff channel user cannot access customer channel data
    Given a policy document with effect "ALLOW" and name "POL.CHANNEL.CUSTOMER.ALLOW.v1" is saved to MongoDB
    Given a subject "human" with id "staff-agent"
    And an action "read"
    And a resource type "account" with id "acc-customer"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "retail" channel "staff"
    And a runtime context with key "resourceChannel" value "customer"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"