@e2e
Feature: Entitlement-Managed Order Service — End-to-End Authorization
  As a sample service consumer
  I want the order service to enforce PDP-driven authorization decisions
  So that access control is enforced consistently front-to-back

  Background:
    Given the order service is running on a random port
    And the sample orders are seeded in MongoDB
    And the PDP rule engine is available in-process

  # ====================================================================
  # SECTION 1: PRECEDENCE — Explicit deny overrides
  # ====================================================================
  @Precedence
  Scenario: Attacker with explicit deny policy receives 403
    Given a DENY policy for subject "attacker" is seeded
    And the request header "X-User-Id" is "attacker"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @Precedence
  Scenario: CSR reads order with PII masked
    Given a baseline ALLOW policy is seeded for subject "csr-user" action "READ" resource "order"
    And the request header "X-User-Id" is "csr-user"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerEmail" should be masked
    And the order field "customerSsn" should be null

  @Precedence
  Scenario: Default deny for unknown user
    Given the request header "X-User-Id" is "unknown-user"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @Precedence
  Scenario: Non-existent order returns 404
    Given a baseline ALLOW policy is seeded for subject "csr-user" action "READ" resource "order"
    And the request header "X-User-Id" is "csr-user"
    When a GET request is sent to "/api/orders/ORD-999"
    Then the response status should be 404

  # ====================================================================
  # SECTION 2: FIELD ACCESS — Admin full visibility
  # ====================================================================
  @FieldAccess
  Scenario: Admin reads order with full PII visibility
    Given a baseline ALLOW policy is seeded for subject "admin-user" action "READ" resource "order"
    And the request header "X-User-Id" is "admin-user"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerEmail" should be visible
    And the order field "customerSsn" should be visible
    And the order field "customerName" should be "Alice Johnson"

  @FieldAccess
  Scenario: List all orders returns accessible orders
    Given a baseline ALLOW policy is seeded for subject "admin-user" action "READ" resource "order"
    And the request header "X-User-Id" is "admin-user"
    When a GET request is sent to "/api/orders"
    Then the response status should be 200
    And the response should be a list
    And the list entry at index 0 field "customerEmail" should be "alice@acme.com"
    And the list entry at index 0 field "customerSsn" should be "123-45-6789"

  # ====================================================================
  # SECTION 3: SERVICE-TO-SERVICE — Workload authorization
  # ====================================================================
  @ServiceAuth
  Scenario: Reporting service reads aggregate data
    Given a WORKLOAD ALLOW policy is seeded for service "reporting-service"
    And the request header "X-Service-Id" is "reporting-service"
    And the request header "X-Service-Type" is "workload"
    When a GET request is sent to "/api/orders/aggregate"
    Then the response status should be 200
    And the aggregate response should contain "totalOrders" value 2

  # ====================================================================
  # SECTION 4: ReBAC — Relationship-based approval
  # ====================================================================
  @ReBAC
  Scenario: Manager can approve order via ReBAC relationship
    Given a ReBAC ALLOW policy is seeded for action "APPROVE" resource "order"
    And a relationship edge from "bob" to "ORD-001" of type "owner" is saved
    And a relationship edge from "bob" to "ORD-001" of type "manages" is saved
    And the request header "X-User-Id" is "bob"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 200
    And the approval response should have status "APPROVED"

  @FieldAccess
  Scenario: Auditor lists orders with PII fully redacted
    Given a baseline ALLOW policy is seeded for subject "auditor" action "READ" resource "order"
    And the request header "X-User-Id" is "auditor"
    When a GET request is sent to "/api/orders"
    Then the response status should be 200
    And the response should be a list
    And the list entry at index 0 field "id" should be "ORD-001"
    And the list entry at index 0 field "customerEmail" should be null
    And the list entry at index 0 field "customerSsn" should be null

  @ReBAC
  Scenario: Non-manager without relationship is denied approve
    Given a ReBAC ALLOW policy is seeded for action "APPROVE" resource "order"
    And the request header "X-User-Id" is "unknown-user"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @ReBAC
  Scenario: Bob is denied approve without manages relationship
    Given a ReBAC ALLOW policy is seeded for action "APPROVE" resource "order"
    And the request header "X-User-Id" is "bob"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 403
    And the response body should contain "Access denied"
