@e2e
Feature: Non-Intrusive Entitlement Order Service — End-to-End Authorization
  As a sample service consumer
  I want the order service to enforce PDP-driven authorization decisions via non-intrusive interceptor
  So that access control is enforced consistently front-to-back without modifying controller code

  Background:
    Given the order service is running on a random port
    And the PDP rule engine is available in-process
    And the PDP policies and relationships are seeded

  # ====================================================================
  # SECTION 1: PRECEDENCE — Explicit deny overrides
  # ====================================================================
  @Precedence
  Scenario: Attacker with explicit deny policy receives 403
    Given the request header "X-User-Id" is "attacker"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @Precedence
  Scenario: Alice reads order with PII masked via interceptor
    Given the request header "X-User-Id" is "alice"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerEmail" should be masked
    And the order field "customerSsn" should be redacted

  @Precedence
  Scenario: Default deny for unknown user
    Given the request header "X-User-Id" is "unknown-user"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @Precedence
  Scenario: Non-existent order returns 404
    Given the request header "X-User-Id" is "alice"
    When a GET request is sent to "/api/orders/ORD-999"
    Then the response status should be 404

  # ====================================================================
  # SECTION 2: FIELD ACCESS — Admin full visibility
  # ====================================================================
  @FieldAccess
  Scenario: Admin reads order with full PII visibility
    Given the request header "X-User-Id" is "admin"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerEmail" should be visible
    And the order field "customerSsn" should be visible
    And the order field "customerName" should be "Alice Johnson"

  @FieldAccess
  Scenario: List all orders returns accessible orders
    Given the request header "X-User-Id" is "admin"
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
    Given the request header "X-Service-Id" is "reporting-service"
    When a GET request is sent to "/api/orders/aggregate"
    Then the response status should be 200
    And the aggregate response should contain "totalOrders" value 3

  # ====================================================================
  # SECTION 4: ReBAC — Relationship-based approval
  # ====================================================================
  @ReBAC
  Scenario: Bob can approve order via manages relationship
    Given the request header "X-User-Id" is "bob"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 200
    And the approval response should have status "APPROVED"

  @ReBAC
  Scenario: Non-manager without relationship is denied approve
    Given the request header "X-User-Id" is "unknown-user"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @ReBAC
  Scenario: User with policy but no relationship is denied approve
    Given the request header "X-User-Id" is "alice"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 403
    And the response body should contain "Access denied"