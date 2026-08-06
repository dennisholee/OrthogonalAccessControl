@cdp @policy-architecture
Feature: Suppression Rule Evaluation
  As a data protection officer
  I want suppression lists to override all other policy evaluations
  So that Do-Not-Contact and Do-Not-Sell preferences are always honoured

  Background:
    Given the policy decision service is running on a random port

  @Suppression
  Scenario: DNC flag blocks marketing contact even with ALLOW policy
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.MARKETING.ALLOW.v1" is saved to MongoDB
    And a suppression flag "DNC" with value "true"
    And a subject "human" with id "customer-123"
    And an action "contact"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_SUPPRESSED"

  @Suppression
  Scenario: DNC flag does not block legal review action
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.LEGAL.ALLOW.v1" for action "legal_review" and resource type "customer-profile" is saved to MongoDB
    And a suppression flag "DNC" with value "true"
    And a subject "human" with id "legal-counsel"
    And an action "legal_review"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Suppression
  Scenario: DNS flag blocks data sale even with consent granted
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.SHARE.ALLOW.v1" is saved to MongoDB
    And a suppression flag "DNS" with value "true"
    And a subject "human" with id "marketing-operator"
    And an action "share"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_SUPPRESSED"

  @Suppression
  Scenario: LITIGATION_HOLD blocks all access except legal review
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.READ.ALLOW.v1" is saved to MongoDB
    And a suppression flag "LITIGATION_HOLD" with value "true"
    And a subject "human" with id "marketing-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_SUPPRESSED"

  @Suppression
  Scenario: LITIGATION_HOLD allows legal review action
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.LEGAL.ALLOW.v1" for action "legal_review" and resource type "customer-profile" is saved to MongoDB
    And a suppression flag "LITIGATION_HOLD" with value "true"
    And a subject "human" with id "legal-counsel"
    And an action "legal_review"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Suppression
  Scenario: DECEASED flag blocks marketing contact
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.CONTACT.ALLOW.v1" is saved to MongoDB
    And a suppression flag "DECEASED" with value "true"
    And a subject "human" with id "campaign-manager"
    And an action "contact"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_SUPPRESSED"
