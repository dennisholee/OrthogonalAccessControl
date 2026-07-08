@e2e @SubjectResolver
Feature: Subject ID Resolution Strategies
  As a platform engineer
  I want the subject resolver to support multiple strategies (header, JWT, delegate, composite)
  So that authentication sources can be configured without code changes

  Background:
    Given the order service is running on a random port
    And the PDP rule engine is available in-process
    And the PDP policies and relationships are seeded

  # ====================================================================
  # SECTION 1: HEADER RESOLVER (default, backward-compatible)
  # ====================================================================
  @HeaderResolver
  Scenario: User ID from X-User-Id header
    Given the request header "X-User-Id" is "alice"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"

  @HeaderResolver
  Scenario: Service ID from X-Service-Id header fallback
    Given the request header "X-Service-Id" is "reporting-service"
    When a GET request is sent to "/api/orders/aggregate"
    Then the response status should be 200
    And the aggregate response should contain "totalOrders" value 3

  @HeaderResolver
  Scenario: Per-operation header override takes priority over user header
    Given the request header "X-Service-Id" is "reporting-service"
    And the request header "X-User-Id" is "attacker"
    When a GET request is sent to "/api/orders/aggregate"
    Then the response status should be 200
    And the aggregate response should contain "totalOrders" value 3

  @HeaderResolver
  Scenario: Missing identity headers yields 403
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Missing identity"

  # ====================================================================
  # SECTION 2: JWT RESOLVER — composite mode, JWT resolves when headers absent
  # ====================================================================
  @JwtResolver
  Scenario: JWT resolves "admin" via "sub" claim when headers absent
    Given a valid JWT token with claim "sub" = "admin"
    And no identity headers are present
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerSsn" should be visible

  @JwtResolver
  Scenario: subjectType "workload" selects oidc schema on getAggregate
    Given a valid JWT token with claim "preferred_username" = "reporting-service"
    And no identity headers are present
    When a GET request is sent to "/api/orders/aggregate"
    Then the response status should be 200
    And the aggregate response should contain "totalOrders" value 3

  @JwtResolver
  Scenario: JWT with missing claim returns 403
    Given a valid JWT token with claim "email" = "admin@acme.com"
    And no identity headers are present
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Missing identity"

  @JwtResolver
  Scenario: Missing Authorization header falls through to header then 403
    Given no Authorization header is present
    And the request header "X-User-Id" is "unknown-user"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Access denied"

  @JwtResolver
  Scenario: Malformed JWT token yields unresolved identity and 403
    Given no identity headers are present
    And the request header "Authorization" is "Bearer invalid-token"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Missing identity"

  @JwtResolver
  Scenario: Whitespace-only JWT token yields 403
    Given no identity headers are present
    And the request header "Authorization" is "Bearer   "
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Missing identity"

  # ====================================================================
  # SECTION 3: DELEGATE RESOLVER — custom extension point
  # ====================================================================
  @DelegateResolver
  Scenario: Custom delegate resolves from X-Custom-Id header
    Given no identity headers are present
    And a custom SubjectResolverDelegate is registered
    And the request header "X-Custom-Id" is "alice"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"

  @DelegateResolver
  Scenario: Delegate returns null and falls through to header
    Given a custom SubjectResolverDelegate that returns null
    And the request header "X-User-Id" is "alice"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"

  @DelegateResolver
  Scenario: Delegate and headers both absent yields 403
    Given a custom SubjectResolverDelegate that returns null
    And no identity headers are present
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 403
    And the response body should contain "Missing identity"

  # ====================================================================
  # SECTION 4: COMPOSITE CHAIN — ordered fallback across all resolvers
  # Header (Order 0) → JWT (Order 10) → Delegate (Order 20)
  # ====================================================================
  @CompositeResolver
  Scenario: Header takes priority over JWT when both are present
    Given the request header "X-User-Id" is "admin"
    And a valid JWT token with claim "sub" = "attacker"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerSsn" should be visible

  @CompositeResolver
  Scenario: JWT fallback when header is absent
    Given a valid JWT token with claim "sub" = "alice"
    And no identity headers are present
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"

  @CompositeResolver
  Scenario: Delegate fallback when header and JWT both absent
    Given no identity headers are present
    And no Authorization header is present
    And a custom SubjectResolverDelegate is registered
    And the request header "X-Custom-Id" is "alice"
    When a GET request is sent to "/api/orders/ORD-001"
    Then the response status should be 200
    And the order field "customerName" should be "Alice Johnson"