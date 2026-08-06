@cdp @policy-architecture
Feature: Subject Consent Rule Evaluation
  As a data protection officer
  I want consent evaluation to gate access to customer data
  So that data is only processed with valid consent under GDPR and CCPA

  Background:
    Given the policy decision service is running on a random port

  @Consent
  Scenario: Withdrawn consent blocks marketing access
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.MARKETING.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a consent version "47"
    And a consent attribute "marketing-consent" with status "WITHDRAWN"
    And a subject "human" with id "customer-123"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CONSENT_WITHDRAWN"

  @Consent
  Scenario: Granted consent allows marketing access
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.MARKETING.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a consent version "47"
    And a consent attribute "marketing-consent" with status "GRANTED"
    And a subject "human" with id "customer-123"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Consent
  Scenario: Expired consent returns DECISION_CONSENT_EXPIRED
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.MARKETING.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a consent version "48"
    And a consent attribute "marketing-consent" with status "EXPIRED"
    And a subject "human" with id "customer-123"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CONSENT_EXPIRED"

  @Consent
  Scenario: Missing consent version requires consent re-fetch
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.MARKETING.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a consent attribute "marketing-consent" with status "GRANTED"
    And a subject "human" with id "customer-123"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CONSENT_REQUIRED"

  @Consent
  Scenario: NOT_PROVIDED consent blocks access
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.MARKETING.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a consent version "47"
    And a consent attribute "marketing-consent" with status "NOT_PROVIDED"
    And a subject "human" with id "customer-123"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CONSENT_WITHDRAWN"

  @Consent
  Scenario: OBJECTION_SUSTAINED blocks processing under GDPR Article 21
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.PROFILING.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a consent version "49"
    And a consent attribute "profiling-consent" with status "OBJECTION_SUSTAINED"
    And a subject "human" with id "customer-123"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff" purpose "analytics" regulatoryRegime "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_OBJECTION_SUSTAINED"

  @Consent
  Scenario: No consent attributes in request skips consent evaluation
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"