@e2e @Extended
Feature: Non-Intrusive Entitlement — Extended Policy Rules
  As a sample service consumer
  I want to verify ABAC, caveat, boundary, break-glass, and relationship policy rules
  So that the full PDP rule chain is exercised through non-intrusive interceptor

  Background:
    Given the order service is running on a random port
    And the PDP rule engine is available in-process
    And the PDP policies and relationships are seeded

  # ====================================================================
  # SECTION 1: ABAC — Department Conditions
  # ====================================================================
  @ABAC
  Scenario: CSR with matching department condition reads order
    Given the request header "X-User-Id" is "csr-user"
    And the request header "X-Department" is "compliance"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"

  @ABAC
  Scenario: CSR with mismatched department condition is denied
    Given the request header "X-User-Id" is "csr-user"
    And the request header "X-Department" is "engineering"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Access denied"

  # ====================================================================
  # SECTION 2: CAVEATS — Time Window
  # ====================================================================
  @Caveats
  Scenario: Time-window caveat blocks access outside business hours
    Given the request header "X-User-Id" is "csr-user"
    And the request header "X-Current-Hour" is "03"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200

  # ====================================================================
  # SECTION 3: BREAK-GLASS
  # ====================================================================
  @BreakGlass
  Scenario: Break-glass activation grants access to order
    Given the request header "X-User-Id" is "break-glass-operator"
    And the request header "X-Break-Glass-Active" is "true"
    And the request header "X-Break-Glass-Reason" is "P1 incident recovery"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200

  # ====================================================================
  # SECTION 4: BOUNDARY ISOLATION
  # ====================================================================
  @Boundary
  Scenario: Cross-tenant boundary denies access
    Given the request header "X-User-Id" is "csr-user"
    And the request header "X-Tenant" is "tenant-b"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Access denied"

  # ====================================================================
  # SECTION 5: HIERARCHICAL ReBAC
  # ====================================================================
  @ReBAC
  Scenario: CEO with 3-hop manages chain can read leaf resource
    Given the request header "X-User-Id" is "CEO"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"

  @ReBAC
  Scenario: ReBAC type mismatch — approver edge fails manages policy
    Given the request header "X-User-Id" is "alice"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 403
    And the response body should contain "Access denied"

  # ====================================================================
  # SECTION 6: SEPARATION OF DUTIES
  # ====================================================================
  @SoD
  Scenario: Requester cannot self-approve transaction
    Given the request header "X-User-Id" is "alice"
    And the request header "X-Requester-Id" is "alice"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @SoD
  Scenario: Different requester and approver succeeds
    Given the request header "X-User-Id" is "bob"
    And the request header "X-Requester-Id" is "alice"
    When a POST request is sent to "/api/orders/ORD-001/approve"
    Then the response status should be 200
    And the approval response should have status "APPROVED"