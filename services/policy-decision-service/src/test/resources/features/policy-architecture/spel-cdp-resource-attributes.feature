@cdp @spel-bindings
Feature: CDP Resource Attributes in SpEL Conditions
  As a policy author
  I want to reference CDP-specific resource attributes in SpEL conditions
  So that policies can condition on data source, data subject category, suppression flags, and consent state

  Background:
    Given the policy decision service is running on a random port

  @CdpSpel
  Scenario: Policy conditions on resource.regulatoryRegime
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.GDPR_ONLY.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "resource.regulatoryRegime == 'GDPR'"
    And a subject "human" with id "analyst-eu"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceRegulatoryRegime" value "GDPR"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CdpSpel
  Scenario: Policy with mismatched resource.regulatoryRegime denies
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.GDPR_ONLY.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "resource.regulatoryRegime == 'GDPR'"
    And a subject "human" with id "analyst-us"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "US" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceRegulatoryRegime" value "CCPA"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "SPEL_CONDITION_FAILED"

  @CdpSpel
  Scenario: Policy conditions on resource.dataSources
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.DATA_SOURCE.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "resource.dataSources.contains('crm')"
    And a subject "human" with id "analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceDataSources" value "[\"crm\", \"website\"]"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CdpSpel
  Scenario: Policy with DENY effect on dataSubjectCategory blocks children
    Given a policy document with effect "DENY" and name "POL.CDP.PROFILE.NO_CHILDREN.DENY.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "resource.dataSubjectCategory == 'CHILD_UNDER_13'"
    And a subject "human" with id "analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceDataSubjectCategory" value "CHILD_UNDER_13"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPLICIT_DENY"

  @CdpSpel
  Scenario: Policy conditions on resource.suppressionFlags
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.NO_HOLD.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "resource.suppressionFlags['LITIGATION_HOLD'] != true"
    And a subject "human" with id "analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceSuppressionFlags" value "{\"LITIGATION_HOLD\": false}"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CdpSpel
  Scenario: Policy conditions on resource.consentAttributes map
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.CONSENT.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "resource.consentAttributes['marketing-consent'].status == 'GRANTED'"
    And a subject "human" with id "marketing-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceConsentAttributes" value "{\"marketing-consent\":{\"status\":\"GRANTED\"}}"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @CdpSpel
  Scenario: Policy conditions on resource.segments
    Given a policy document with effect "ALLOW" and name "POL.CDP.PROFILE.SEGMENTS.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And the policy has spelCondition "resource.segments.contains('VIP_CUSTOMERS')"
    And a subject "human" with id "segment-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceSegments" value "[\"REGULAR\", \"VIP_CUSTOMERS\"]"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
