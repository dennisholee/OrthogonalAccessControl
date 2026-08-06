@cdp @caveats @export @aggregation
Feature: Export Restriction and Aggregation Caveats
  As a CDP platform architect
  I want egress destinations validated against authorisation constraints and small
  result sets gated by aggregation thresholds
  So that unauthorised exports and re-identification via small segments are blocked

  Background:
    Given the policy decision service is running on a random port

  Scenario: Export to an unlisted destination is denied
    Given a policy document with effect "ALLOW" and name "POL.CDP.EXPORT.ALLOW.v1" for action "export" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "export-user"
    And an action "export"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "exportDestination" value "facebook-custom-audience"
    And a runtime context with key "destinationConstraints" value "{\"internal-warehouse\":{}}"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPORT_RESTRICTED"

  Scenario: Export to a listed destination is allowed
    Given a policy document with effect "ALLOW" and name "POL.CDP.EXPORT.ALLOW.v1" for action "export" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "export-user"
    And an action "export"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "exportDestination" value "internal-warehouse"
    And a runtime context with key "destinationConstraints" value "{\"internal-warehouse\":{}}"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  Scenario: GDPR export with an expired transfer mechanism is blocked
    Given a policy document with effect "ALLOW" and name "POL.CDP.EXPORT.ALLOW.v1" for action "export" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "export-user"
    And an action "export"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff" purpose "*" regulatoryRegime "GDPR"
    And a runtime context with key "exportDestination" value "google-ads"
    And a runtime context with key "destinationConstraints" value "{\"google-ads\":{\"transferMechanism\":{\"type\":\"SCC-2021-C2P\",\"validUntil\":\"2020-01-01T00:00:00Z\"}}}"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CROSS_BORDER_TRANSFER_BLOCKED"

  Scenario: GDPR export with a valid transfer mechanism is allowed
    Given a policy document with effect "ALLOW" and name "POL.CDP.EXPORT.ALLOW.v1" for action "export" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "export-user"
    And an action "export"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "EU" market "retail" lineOfBusiness "cards" channel "staff" purpose "*" regulatoryRegime "GDPR"
    And a runtime context with key "exportDestination" value "google-ads"
    And a runtime context with key "destinationConstraints" value "{\"google-ads\":{\"transferMechanism\":{\"type\":\"SCC-2021-C2P\",\"validUntil\":\"2099-01-01T00:00:00Z\"}}}"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  Scenario: Result set below the aggregation threshold is denied
    Given a policy document with effect "ALLOW" and name "POL.CDP.ANALYTICS.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "agg-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "minGroupSize" value "50"
    And a runtime context with key "resultSize" value "10"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_AGGREGATION_REQUIRED"

  Scenario: Result set at or above the aggregation threshold is allowed
    Given a policy document with effect "ALLOW" and name "POL.CDP.ANALYTICS.ALLOW.v1" for action "read" and resource type "customer-profile" is saved to MongoDB
    And a subject "human" with id "agg-analyst"
    And an action "read"
    And a resource type "customer-profile" with id "profile-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "minGroupSize" value "50"
    And a runtime context with key "resultSize" value "100"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
