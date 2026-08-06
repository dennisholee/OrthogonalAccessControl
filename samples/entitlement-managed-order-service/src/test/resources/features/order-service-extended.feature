@e2e @Extended
Feature: Entitlement-Managed Order Service — Extended Policy Rules
  As a sample service consumer
  I want to verify ABAC, caveat, boundary, break-glass, and relationship policy rules
  So that the full PDP rule chain is exercised through sample integration

  Background:
    Given the order service is running on a random port
    And the sample orders are seeded in MongoDB
    And the PDP rule engine is available in-process

  # ====================================================================
  # SECTION 1: ABAC / ATTRIBUTE-BASED ACCESS
  # ====================================================================
  @ABAC
  Scenario: CSR with matching department condition reads order
    Given a SpEL-ALLOW policy is seeded for subject "csr-user" with condition "subject.department == 'compliance'"
    And the request header "X-User-Id" is "csr-user"
    And the request header "X-Department" is "compliance"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"

  @ABAC
  Scenario: CSR with mismatched department condition is denied
    Given a SpEL-ALLOW policy is seeded for subject "csr-user" with condition "subject.department == 'hr'"
    And the request header "X-User-Id" is "csr-user"
    And the request header "X-Department" is "engineering"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @ABAC
  Scenario: Combined ABAC conditions all pass
    Given a SpEL-ALLOW policy is seeded for subject "csr-user" with condition "subject.department == 'compliance' && environment.currentHour >= 8"
    And the request header "X-User-Id" is "csr-user"
    And the request header "X-Department" is "compliance"
    And the request header "X-Current-Hour" is "14"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"

  # ====================================================================
  # SECTION 2: CAVEATS — Time Window
  # ====================================================================
  @Caveats
  Scenario: Time-window caveat blocks access outside business hours
    Given a time-window ALLOW policy is seeded for subject "csr-user" with window "09:00-17:00 UTC"
    And the request header "X-User-Id" is "csr-user"
    And the request header "X-Current-Hour" is "03"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerSsn" should be null

  # ====================================================================
  # SECTION 3: BREAK-GLASS EMERGENCY ACCESS
  # ====================================================================
  @BreakGlass
  Scenario: Break-glass activation grants access to order
    Given a break-glass ALLOW policy is seeded for action "read" resource "order"
    And the request header "X-User-Id" is "break-glass-operator"
    And the request header "X-Break-Glass-Active" is "true"
    And the request header "X-Break-Glass-Reason" is "P1 incident recovery"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200

  @BreakGlass
  Scenario: Break-glass without active flag returns deny
    Given a baseline ALLOW policy is seeded for subject "break-glass-operator" action "read" resource "order"
    And the request header "X-User-Id" is "break-glass-operator"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"

  # ====================================================================
  # SECTION 4: BOUNDARY ISOLATION
  # ====================================================================
  @Boundary
  Scenario: Cross-tenant boundary denies access
    Given a tenant-scoped ALLOW policy is seeded for subject "csr-user" tenant "tenant-a"
    And the request header "X-User-Id" is "csr-user"
    And the request header "X-Tenant" is "tenant-b"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @Boundary
  Scenario: Cross-channel boundary — staff cannot access customer channel
    Given a channel-scoped ALLOW policy is seeded for subject "csr-user" channel "staff"
    And the request header "X-User-Id" is "csr-user"
    And the request header "X-Channel" is "customer"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Access denied"

  # ====================================================================
  # SECTION 5: HIERARCHICAL ReBAC (3-hop chain)
  # ====================================================================
  @ReBAC
  Scenario: CEO with 3-hop manages chain can read leaf resource
    Given a ReBAC ALLOW policy is seeded for action "read" resource "order"
    And a relationship chain "CEO->VP->Director->CSR:manages" is saved
    And a relationship edge from "CSR" to "ORD-001" of type "manages" is saved
    And the request header "X-User-Id" is "CEO"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"

  @ReBAC
  Scenario: ReBAC type mismatch — approver edge fails manages policy
    Given a ReBAC ALLOW policy is seeded for action "approve" resource "order"
    And a relationship edge from "alice" to "ORD-001" of type "approver" is saved
    And the request header "X-User-Id" is "alice"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 403
    And the response body should contain "Access denied"

  # ====================================================================
  # SECTION 6: SEPARATION OF DUTIES
  # ====================================================================
  @SoD
  Scenario: Requester cannot self-approve transaction
    Given a SpEL-ALLOW policy is seeded for subject "alice" with condition "subject.id != resource.requesterId"
    And the request header "X-User-Id" is "alice"
    And the request header "X-Requester-Id" is "alice"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @SoD
  Scenario: Different requester and approver succeeds
    Given a SpEL-ALLOW policy is seeded for subject "bob-manager" with condition "subject.id != resource.requesterId"
    And the request header "X-User-Id" is "bob-manager"
    And the request header "X-Requester-Id" is "alice"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 200
    And the approval response should have status "APPROVED"