@cdp @policy-architecture
Feature: Purpose Boundary Dimension
  As a data protection officer
  I want purpose to be a first-class boundary dimension
  So that data is only processed for the declared purpose

  Background:
    Given the policy decision service is running on a random port

  @Purpose
  Scenario: Marketing-scoped policy allows marketing purpose access
    Given a CDP policy document with purpose "marketing-campaign" is saved to MongoDB
    And a subject "human" with id "campaign-manager"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Purpose
  Scenario: Marketing-scoped policy denies analytics purpose access
    Given a CDP policy document with purpose "marketing-campaign" is saved to MongoDB
    And a subject "human" with id "campaign-manager"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "analytics" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @Purpose
  Scenario: Policy without purpose scope matches any purpose
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @RegulatoryRegime
  Scenario: GDPR policy scopes by regulatory regime
    Given a CDP policy document with regulatory regime "GDPR" is saved to MongoDB
    And a CDP policy document with purpose "marketing-campaign" is saved to MongoDB
    And a subject "human" with id "campaign-manager"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @RegulatoryRegime
  Scenario: Regulatory regime mismatch triggers boundary deny
    Given a CDP policy document with regulatory regime "GDPR" is saved to MongoDB
    And a subject "human" with id "campaign-manager"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign" regulatoryRegime "CCPA"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @Purpose
  Scenario: Purpose array scoping uses IN semantics
    Given a policy document with effect "ALLOW" and name "POL.CDP.PURPOSE.ARRAY.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has purpose array ["marketing-campaign", "customer-support"]
    And a subject "human" with id "support-agent"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "customer-support" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Purpose
  Scenario: Purpose wildcard "*" matches any purpose
    Given a policy document with effect "ALLOW" and name "POL.CDP.PURPOSE.WILDCARD.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has purpose "*"
    And a subject "human" with id "generic-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "dsar-fulfilment" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @RegulatoryRegime
  Scenario: Regulatory regime array scoping supports multiple regimes
    Given a policy document with effect "ALLOW" and name "POL.CDP.REGIME.ARRAY.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has regulatoryRegime array ["GDPR", "CCPA"]
    And a subject "human" with id "us-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "analytics" regulatoryRegime "CCPA"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Purpose @RegulatoryRegime
  Scenario: Compound purpose and regime mismatch denies access
    Given a policy document with effect "ALLOW" and name "POL.CDP.COMPOUND.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has purpose "marketing-campaign"
    And the policy has regulatoryRegime "GDPR"
    And a subject "human" with id "marketing-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign" regulatoryRegime "CCPA"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"