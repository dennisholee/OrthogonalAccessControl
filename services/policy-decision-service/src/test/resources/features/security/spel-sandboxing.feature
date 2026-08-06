@security @sandboxing
Feature: SpEL Expression Sandboxing
  As a platform security administrator
  I want SpEL expressions restricted to prevent remote code execution
  So that no policy author can execute arbitrary JVM code

  Background:
    Given the policy decision service is running on a random port

  @Sandboxing
  Scenario: Type references are blocked in SpEL expressions
    Given a policy document with effect "ALLOW" and name "POL.SEC.RCE.TEST.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has spelCondition "T(java.lang.Runtime).getRuntime().exec('echo pwned')"
    And a subject "human" with id "attacker"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "SPEL_EVALUATION_ERROR"

  @Sandboxing
  Scenario: Constructor invocations are blocked
    Given a policy document with effect "ALLOW" and name "POL.SEC.CONSTRUCTOR.TEST.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has spelCondition "new java.io.File('/tmp/exploit')"
    And a subject "human" with id "attacker"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "SPEL_EVALUATION_ERROR"

  @Sandboxing
  Scenario: Static method access is blocked
    Given a policy document with effect "ALLOW" and name "POL.SEC.STATIC.TEST.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has spelCondition "T(Thread).sleep(10000)"
    And a subject "human" with id "attacker"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "SPEL_EVALUATION_ERROR"

  @Sandboxing
  Scenario: Bean references are blocked
    Given a policy document with effect "ALLOW" and name "POL.SEC.BEAN.TEST.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has spelCondition "@anyBean.execute()"
    And a subject "human" with id "attacker"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "DENY" with code "SPEL_EVALUATION_ERROR"

  @Sandboxing
  Scenario: Legitimate property access still works after sandboxing
    Given a policy document with effect "ALLOW" and name "POL.SEC.LEGIT.ALLOW.v1" for action "read" and resource type "account" is saved to MongoDB
    And the policy has spelCondition "subject.department == 'compliance'"
    And a subject "human" with id "compliance-officer"
    And an action "read"
    And a resource type "account" with id "acc-1"
    And a boundary context tenant "tenant-a" geography "us" market "retail" lineOfBusiness "cards" channel "staff"
    And a runtime context with key "subjectDepartment" value "compliance"
    When a check permission request is sent via HTTP
    Then the response status should be 200
    And the decision should be "ALLOW"
