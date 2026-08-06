@cdp @policy-architecture @controller-purpose
Feature: Controller Purpose Validation
  As a data protection officer
  I want purposes validated against the controller's registered purpose scope
  So that processing outside the controller's instructions is prevented

  Background:
    Given the policy decision service is running on a random port

  @ControllerPurpose
  Scenario: Purpose registered for controller allows processing
    Given a controller purpose "marketing-campaign" is registered for tenant "tenant-a" with lawful basis "GDPR-Art6-1a"
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.READ.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "marketing-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @ControllerPurpose
  Scenario: Purpose not registered for controller denies processing
    Given a controller purpose "marketing-campaign" is registered for tenant "tenant-a" with lawful basis "GDPR-Art6-1a"
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.READ.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "fraud-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff" purpose "fraud-detection"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_PURPOSE_NOT_AUTHORISED_FOR_CONTROLLER"

  @ControllerPurpose
  Scenario: Purpose registered for different tenant does not allow processing
    Given a controller purpose "analytics" is registered for tenant "tenant-a" with lawful basis "GDPR-Art6-1a"
    Given a controller purpose "marketing-campaign" is registered for tenant "tenant-b" with lawful basis "GDPR-Art6-1a"
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.READ.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "marketing-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff" purpose "marketing-campaign"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_PURPOSE_NOT_AUTHORISED_FOR_CONTROLLER"

  @ControllerPurpose
  Scenario: Request without purpose skips controller validation
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And a subject "human" with id "eff-user"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
