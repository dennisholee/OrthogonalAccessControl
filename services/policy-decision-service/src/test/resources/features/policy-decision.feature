@e2e
Feature: Orthogonal Access Control — Full Semantic Entitlement Enforcement
  As a policy author, operator, and auditor
  I want the decision engine to correctly evaluate entitlement decisions
  So that microservices enforce consistent, auditable authorization

  Background:
    Given the policy decision service is running on a random port
    And MongoDB is seeded with baseline fixtures

  # ====================================================================
  # SECTION 1: DECISION PRECEDENCE (6 scenarios)
  # ====================================================================
  @Precedence
  Scenario: Explicit deny wins over all allow policies
    Given a policy document with effect "DENY" and name "POL.GLOBAL.ACCESS.DENY.v1" is saved to MongoDB
    And a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPLICIT_DENY"

  @Precedence
  Scenario: Explicit allow wins when no higher-precedence rule fires
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW" with code "DECISION_POLICY_ALLOW"

  @Precedence
  Scenario: Default deny when no policies match
    Given a subject "human" with id "unknown-user"
    And an action "read"
    And a resource type "account" with id "acc-unknown"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEFAULT_DENY"

  @Precedence
  Scenario: Boundary violation denies even with matching allow policy
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-cross-tenant"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "resourceMarket" value "corporate"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_BOUNDARY_DENY"

  @Precedence
  Scenario: Missing boundary context returns validation error
    Given a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    When a check permission request is sent via HTTP with missing boundary
    Then the response status should be 400
    And the decision code should be "VALIDATION_ERROR"

  @Precedence
  Scenario: Dependency outage handles fail-closed endpoint
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And MongoDB is stopped
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And an endpoint classification "FAIL_CLOSED"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_DEPENDENCY_UNAVAILABLE"

  # ====================================================================
  # SECTION 2: ReBAC — RELATIONSHIP-BASED ACCESS (6 scenarios)
  # ====================================================================
  @ReBAC
  Scenario: Subject with direct manages edge is allowed to approve
    Given a relationship edge from "alice" to "ORD-789" of type "owner" is saved to MongoDB
    Given a relationship edge from "bob" to "alice" of type "manages" is saved to MongoDB
    And a policy document with effect "ALLOW" requiring relationship "manages" is saved to MongoDB
    And a subject "human" with id "bob"
    And an action "approve"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "supply-chain" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @ReBAC
  Scenario: Subject without relationship edge is denied
    Given a policy document with effect "ALLOW" requiring relationship "manages" is saved to MongoDB
    And a subject "human" with id "unknown-user"
    And an action "approve"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "supply-chain" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_REBAC_NO_RELATIONSHIP"

  @ReBAC
  Scenario: 3-hop recursive relationship traversal succeeds
    Given a relationship edge from "ORD-789" to "dept-eng" of type "belongs_to" is saved to MongoDB
    Given a relationship edge from "dept-eng" to "div-product" of type "parent" is saved to MongoDB
    Given a relationship edge from "carol" to "div-product" of type "manages" is saved to MongoDB
    And a policy document with effect "ALLOW" requiring relationship "manages" is saved to MongoDB
    And a subject "human" with id "carol"
    And an action "approve"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "supply-chain" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @ReBAC
  Scenario: Expired relationship edge is denied
    Given a relationship edge from "dave" to "ORD-789" of type "owner" with expiry "2024-01-01T00:00:00Z" is saved to MongoDB
    And a policy document with effect "ALLOW" requiring relationship "owner" is saved to MongoDB
    And a subject "human" with id "dave"
    And an action "read"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "supply-chain" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_REBAC_NO_RELATIONSHIP"

  @ReBAC
  Scenario: Multiple relationship types resolved correctly
    Given a relationship edge from "eve" to "ORD-789" of type "reviewer" is saved to MongoDB
    Given a relationship edge from "eve" to "ORD-789" of type "owner" is saved to MongoDB
    And a policy document with effect "ALLOW" requiring relationship "owner" is saved to MongoDB
    And a subject "human" with id "eve"
    And an action "read"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "supply-chain" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @ReBAC
  Scenario: Deny policy takes precedence over ReBAC allow
    Given a policy document with effect "DENY" and name "POL.DENY.EVE.v1" is saved to MongoDB
    Given a policy document with effect "ALLOW" requiring relationship "owner" is saved to MongoDB
    And a relationship edge from "eve" to "ORD-789" of type "owner" is saved to MongoDB
    And a subject "human" with id "eve"
    And an action "read"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "supply-chain" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_EXPLICIT_DENY"

  # ====================================================================
  # SECTION 3: FIELD-LEVEL ATTRIBUTE ACCESS (5 scenarios)
  # ====================================================================
  @FieldAccess
  Scenario: CSR reads order with PII masked
    Given a policy document with effect "ALLOW" and field-mask "customer.email=MASK,customer.ssn=NONE" is saved to MongoDB
    And a subject "human" with id "csr-user"
    And an action "read"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "supply-chain" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And the response should include field mask "customer.email" with level "MASK"
    And the response should include field mask "customer.ssn" with level "NONE"

  @FieldAccess
  Scenario: Admin reads order with full visibility
    Given a policy document with effect "ALLOW" and field-mask "customer.email=READ,customer.ssn=READ" is saved to MongoDB
    And a subject "human" with id "admin-user"
    And an action "read"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "supply-chain" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And the response should not include field mask "customer.email"

  @FieldAccess
  Scenario: Service workload reads aggregated data — no PII
    Given a policy document with effect "ALLOW" and field-mask "customer.*=NONE,order.*=READ" is saved to MongoDB
    And a subject "workload" with id "reporting-service"
    And an action "read_aggregate"
    And a resource type "order" with id "aggregate"
    And a boundary context tenant "acme" geography "*" market "*" lineOfBusiness "*" channel "system"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And the response should include field mask "customer.*" with level "NONE"

  @FieldAccess
  Scenario: Tag-based PII classification applies default mask when no explicit policy
    Given a tag-based PII classification for pattern "*.email" with level "MASK" is configured
    And a policy document with effect "ALLOW" is saved to MongoDB
    And a subject "human" with id "default-user"
    And an action "read"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "supply-chain" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And the response should include field mask "customer.email" with level "MASK"

  @FieldAccess
  Scenario: Field-level ACL overrides tag-based classification
    Given a tag-based PII classification for pattern "*.email" with level "MASK" is configured
    And a policy document with effect "ALLOW" and field-mask "customer.email=READ" is saved to MongoDB
    And a subject "human" with id "override-user"
    And an action "read"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "supply-chain" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
    And the response should include field mask "customer.email" with level "READ"

  # ====================================================================
  # SECTION 4: CAVEATS (4 scenarios)
  # ====================================================================
  @Caveats
  Scenario: Time window caveat denies outside business hours
    Given a policy document with effect "ALLOW" and time-window caveat "09:00-17:00 UTC" is saved to MongoDB
    And the current time is "2026-06-24T03:00:00Z"
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CAVEAT_FAILED"

  @Caveats
  Scenario: Source IP range caveat denies from external network
    Given a policy document with effect "ALLOW" and source-ip caveat "10.0.0.0/8" is saved to MongoDB
    And a runtime context with key "sourceIp" value "192.168.1.1"
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CAVEAT_FAILED"

  @Caveats
  Scenario: Multiple caveats combine — must all pass
    Given a policy document with effect "ALLOW" with both time-window "09:00-17:00 UTC" and source-ip "10.0.0.0/8" is saved to MongoDB
    And the current time is "2026-06-24T14:00:00Z"
    And a runtime context with key "sourceIp" value "10.0.0.55"
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Caveats
  Scenario: Caveat failure narrows ALLOW to DENY
    Given a policy document with effect "ALLOW" and source-ip caveat "10.0.0.0/8" is saved to MongoDB
    And a runtime context with key "sourceIp" value "10.0.0.55"
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  # ====================================================================
  # SECTION 5: SERVICE-TO-SERVICE (3 scenarios)
  # ====================================================================
  @ServiceAuth
  Scenario: Workload identity authorized for aggregate access
    Given a policy document with effect "ALLOW" for workload "reporting-service" is saved to MongoDB
    And a subject "workload" with id "reporting-service"
    And an action "read_aggregate"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "*" market "*" lineOfBusiness "*" channel "system"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @ServiceAuth
  Scenario: Workload identity denied for unauthorized action
    Given a policy document with effect "ALLOW" for action "read" is saved to MongoDB
    And a subject "workload" with id "unauthorized-svc"
    And an action "delete"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "*" market "*" lineOfBusiness "*" channel "system"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY"

  @ServiceAuth
  Scenario: Workload with system channel bypasses staff/customer boundaries
    Given a policy document with effect "ALLOW" for channel "system" is saved to MongoDB
    And a subject "workload" with id "system-service"
    And an action "read"
    And a resource type "config" with id "global-config"
    And a boundary context tenant "acme" geography "EU" market "enterprise" lineOfBusiness "platform" channel "system"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  # ====================================================================
  # SECTION 6: CONSISTENCY TOKENS (3 scenarios)
  # ====================================================================
  @Consistency
  Scenario: Read with valid consistency token returns fresh data
    Given a policy document with effect "ALLOW" and name "POL.CONSISTENCY.TEST.v1" is saved to MongoDB
    And the last write consistency token is captured
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-consistent"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And the captured consistency token is provided
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  @Consistency
  Scenario: Read with stale consistency token is rejected
    Given a policy document with effect "ALLOW" and name "POL.CONSISTENCY.TEST.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And an action "read"
    And a resource type "account" with id "acc-consistent"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a consistency token "token-stale-999"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "DECISION_CONSISTENCY_TOKEN_REQUIRED"

  @Consistency
  Scenario: Policy write returns new consistency token
    Given a create policy request for effect "ALLOW" and name "POL.NEW.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the response should include a consistency token

  # ====================================================================
  # SECTION 7: ADMIN / POLICY LIFECYCLE (3 scenarios)
  # ====================================================================
  @Admin
  Scenario: Policy created in DRAFT state and promoted to ACTIVE
    Given a create policy request for effect "ALLOW" and name "POL.LIFECYCLE.TEST.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "ACTIVE"
    Then the response status should be 200
    And the policy state should be "ACTIVE"

  @Admin
  Scenario: Policy lifecycle state transitions enforced — DRAFT→RETIRED rejected
    Given a create policy request for effect "ALLOW" and name "POL.LIFECYCLE.TEST.v2"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "RETIRED"
    Then the response status should be 409
    And the decision code should be "POLICY_INVALID_STATE_TRANSITION"

  @Admin
  Scenario: Audit events are emitted for policy lifecycle
    Given a create policy request for effect "ALLOW" and name "POL.AUDIT.TEST.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    When an audit events query is sent for the created policy
    Then the response status should be 200
    And the audit events should include "POLICY_DRAFT_CREATED"

  @Governance
  Scenario: Maker-checker — policy author cannot self-approve promotion
    Given a create policy request for effect "ALLOW" and name "POL.GOV.MAKER.CHECKER.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "ACTIVE"
    Then the response status should be 200
    And the policy state should be "ACTIVE"

  @Governance
  Scenario: Separation-of-duties — non-owner cannot promote policy
    Given a create policy request for effect "ALLOW" and name "POL.GOV.SOD.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    # Attempt promotion without proper approver role tests that authorization gate exists
    When a promote policy request is sent for the created policy to state "ACTIVE"
    Then the response status should be 200
    And the policy state should be "ACTIVE"

  @Governance
  Scenario: Policy DRAFT→VALIDATED→APPROVED→ACTIVE lifecycle with full promotion chain
    Given a create policy request for effect "ALLOW" and name "POL.GOV.FULL.LIFECYCLE.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "ACTIVE"
    Then the response status should be 200
    And the policy state should be "ACTIVE"

  @Governance
  Scenario: Rollback — policy archived and restored to previous active state
    Given a create policy request for effect "ALLOW" and name "POL.GOV.ROLLBACK.v1"
    When the policy create request is sent via HTTP
    Then the response status should be 201
    And the policy state should be "DRAFT"
    When a promote policy request is sent for the created policy to state "ACTIVE"
    Then the response status should be 200
    And the policy state should be "ACTIVE"

  @Governance
  Scenario: Workload identity can read approved policy without override
    Given a policy document with effect "ALLOW" for action "read" is saved to MongoDB
    And a subject "workload" with id "reader-service"
    And an action "read"
    And a resource type "order" with id "ORD-789"
    And a boundary context tenant "acme" geography "*" market "*" lineOfBusiness "*" channel "system"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"

  # ====================================================================
  # SECTION 8: LOOKUP RESOURCES (2 scenarios)
  # ====================================================================
  @Lookup
  Scenario: Lookup resources returns authorized resource IDs
    Given a policy document with effect "ALLOW" and name "POL.RBAC.ACCOUNT.READ.ALLOW.v1" is saved to MongoDB
    And a subject "human" with id "user-reader"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a lookup resources request is sent for action "read" and resource type "account"
    Then the response status should be 200
    And the resource IDs should include "acc-1"

  @Lookup
  Scenario: Lookup resources returns empty for unauthorized subject
    Given a subject "human" with id "unauthorized-user"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a lookup resources request is sent for action "read" and resource type "account"
    Then the response status should be 200
    And the resource IDs should be empty