@cdp @domain-membership
Feature: Principal Multi-Domain Membership Validation
  As a platform security administrator
  I want principal domain memberships validated before granting access
  So that a principal cannot access resources in domains they are not authorised for

  Background:
    Given the policy decision service is running on a random port

  @DomainMembership
  Scenario: Principal can access resource within their membership domain
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.READ.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "retail-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceTenant" value "tenant-a"
    And a runtime context with key "resourceGeography" value "EU"
    And a runtime context with key "resourceMarket" value "retail"
    And a runtime context with key "resourceLineOfBusiness" value "cards"
    And a runtime context with key "resourceChannel" value "staff"
    And principal domain membership tenants "tenant-a" markets "retail" geographies "EU" linesOfBusiness "cards" channels "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @DomainMembership
  Scenario: Principal is denied access to resource outside their membership domain
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.READ.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "retail-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-b" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceTenant" value "tenant-b"
    And a runtime context with key "resourceGeography" value "EU"
    And a runtime context with key "resourceMarket" value "retail"
    And a runtime context with key "resourceLineOfBusiness" value "cards"
    And a runtime context with key "resourceChannel" value "staff"
    And principal domain membership tenants "tenant-a" markets "retail" geographies "EU" linesOfBusiness "cards" channels "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DOMAIN_NOT_IN_SCOPE"

  @DomainMembership
  Scenario: Principal with empty memberships cannot access any domain
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.READ.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "no-domain-user"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And principal domain membership tenants "" markets "" geographies "" linesOfBusiness "" channels ""
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DOMAIN_NOT_IN_SCOPE"

  @DomainMembership
  Scenario: Principal memberships absent from request skips validation
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.READ.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "no-membership-user"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @DomainMembership
  Scenario: Multiple dimension mismatch denies access
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.READ.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "retail-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-b" geography "US" market "enterprise" lineOfBusiness "payments" channel "customer"
    And a runtime context with key "resourceTenant" value "tenant-b"
    And a runtime context with key "resourceGeography" value "US"
    And a runtime context with key "resourceMarket" value "enterprise"
    And a runtime context with key "resourceLineOfBusiness" value "payments"
    And a runtime context with key "resourceChannel" value "customer"
    And principal domain membership tenants "tenant-a" markets "retail" geographies "EU" linesOfBusiness "cards" channels "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DOMAIN_NOT_IN_SCOPE"

  @DomainMembership
  Scenario: Purpose membership validation blocks unapproved purpose
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.READ.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "marketing-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff" purpose "fraud-detection"
    And a runtime context with key "resourcePurpose" value "fraud-detection"
    And principal domain membership tenants "tenant-a" markets "retail" geographies "EU" linesOfBusiness "cards" channels "staff" purposes "marketing-campaign"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DOMAIN_NOT_IN_SCOPE"

