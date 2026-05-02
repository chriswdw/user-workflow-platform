Feature: Workflow type submission API

  Background:
    Given I am authenticated as user "alice" with role "ANALYST" for tenant "tenant-1"

  Scenario: Create a new submission
    When I POST /api/v1/workflow-type-submissions with body {"workflowType":"TRADE_BREAK","displayName":"Trade Break","description":"desc"}
    Then the response status is 201
    And the response contains statusCode "DRAFT"

  Scenario: Create duplicate submission returns 409
    Given a submission for workflow type "TRADE_BREAK" exists for tenant "tenant-1"
    When I POST /api/v1/workflow-type-submissions with body {"workflowType":"TRADE_BREAK","displayName":"Trade Break","description":"desc"}
    Then the response status is 409

  Scenario: Get submission by ID
    Given a draft submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When I GET /api/v1/workflow-type-submissions/sub-1
    Then the response status is 200
    And the response contains statusCode "DRAFT"

  Scenario: Get unknown submission returns 404
    When I GET /api/v1/workflow-type-submissions/nonexistent
    Then the response status is 404

  Scenario: Save draft progress
    Given a draft submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When I PATCH /api/v1/workflow-type-submissions/sub-1 with body {"currentStep":2,"draftConfigs":{}}
    Then the response status is 200

  Scenario: Save draft for unknown submission returns 404
    When I PATCH /api/v1/workflow-type-submissions/nonexistent with body {"currentStep":1,"draftConfigs":{}}
    Then the response status is 404

  Scenario: Submit for approval
    Given a complete draft submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When I POST /api/v1/workflow-type-submissions/sub-1/submit with body {}
    Then the response status is 200
    And the response contains statusCode "PENDING_APPROVAL"

  Scenario: Submit incomplete submission returns 422
    Given a draft submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When I POST /api/v1/workflow-type-submissions/sub-1/submit with body {}
    Then the response status is 422

  Scenario: Submit already-submitted submission returns 422
    Given a pending submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When I POST /api/v1/workflow-type-submissions/sub-1/submit with body {}
    Then the response status is 422

  Scenario: Approve submission
    Given a pending submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    And I am authenticated as user "bob" with role "PLATFORM_ADMIN" for tenant "tenant-1"
    When I POST /api/v1/workflow-type-submissions/sub-1/approve with body {}
    Then the response status is 200
    And the response contains statusCode "APPROVED"

  Scenario: Self-approval returns 403
    Given a pending submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When I POST /api/v1/workflow-type-submissions/sub-1/approve with body {}
    Then the response status is 403

  Scenario: Reject submission
    Given a pending submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    And I am authenticated as user "bob" with role "PLATFORM_ADMIN" for tenant "tenant-1"
    When I POST /api/v1/workflow-type-submissions/sub-1/reject with body {"reason":"Needs more detail"}
    Then the response status is 200
    And the response contains statusCode "REJECTED"

  Scenario: Revise rejected submission
    Given a rejected submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When I POST /api/v1/workflow-type-submissions/sub-1/revise with body {}
    Then the response status is 200
    And the response contains statusCode "DRAFT"

  Scenario: Revise unknown submission returns 404
    When I POST /api/v1/workflow-type-submissions/nonexistent/revise with body {}
    Then the response status is 404

  Scenario: Revise non-rejected submission returns 422
    Given a draft submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When I POST /api/v1/workflow-type-submissions/sub-1/revise with body {}
    Then the response status is 422

  Scenario: Submit unknown submission returns 404
    When I POST /api/v1/workflow-type-submissions/nonexistent/submit with body {}
    Then the response status is 404

  Scenario: Approve unknown submission returns 404
    And I am authenticated as user "bob" with role "PLATFORM_ADMIN" for tenant "tenant-1"
    When I POST /api/v1/workflow-type-submissions/nonexistent/approve with body {}
    Then the response status is 404

  Scenario: Approve non-pending submission returns 422
    Given a draft submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    And I am authenticated as user "bob" with role "PLATFORM_ADMIN" for tenant "tenant-1"
    When I POST /api/v1/workflow-type-submissions/sub-1/approve with body {}
    Then the response status is 422

  Scenario: Get pending submissions
    Given a pending submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    And I am authenticated as user "bob" with role "PLATFORM_ADMIN" for tenant "tenant-1"
    When I GET /api/v1/workflow-type-submissions/pending
    Then the response status is 200
    And the response is a non-empty JSON array

  Scenario: Get my drafts
    Given a draft submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When I GET /api/v1/workflow-type-submissions/my-drafts
    Then the response status is 200
    And the response is a non-empty JSON array

  Scenario: Get my rejected submissions
    Given a rejected submission "sub-1" exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When I GET /api/v1/workflow-type-submissions/my-rejected
    Then the response status is 200
    And the response is a non-empty JSON array

  Scenario: Unauthenticated access returns 401
    Given I am not authenticated
    When I POST /api/v1/workflow-type-submissions with body {"workflowType":"TRADE_BREAK","displayName":"x","description":"y"}
    Then the response status is 401
