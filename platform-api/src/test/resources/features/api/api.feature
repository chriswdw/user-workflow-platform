Feature: Work item API

  Background:
    Given the system has a work item "wi-100" for tenant "tenant-1" in state "UNDER_REVIEW"
    And the workflow allows transition "close-as-resolved" from "UNDER_REVIEW" to "CLOSED"

  Scenario: Authenticated user retrieves a work item
    Given I am authenticated as user "user-1" with role "ANALYST" for tenant "tenant-1"
    When I GET /api/v1/work-items/wi-100
    Then the response status is 200
    And the response contains work item id "wi-100"

  Scenario: Unauthenticated request is rejected
    Given I am not authenticated
    When I GET /api/v1/work-items/wi-100
    Then the response status is 401

  Scenario: Requesting an unknown work item returns 404
    Given I am authenticated as user "user-1" with role "ANALYST" for tenant "tenant-1"
    When I GET /api/v1/work-items/wi-nonexistent
    Then the response status is 404

  Scenario: Authenticated user triggers a transition
    Given I am authenticated as user "user-1" with role "ANALYST" for tenant "tenant-1"
    When I POST /api/v1/work-items/wi-100/transitions with body {"transition":"close-as-resolved"}
    Then the response status is 200
    And the response contains work item status "CLOSED"

  Scenario: Transition forbidden for insufficient role returns 403
    Given I am authenticated as user "user-2" with role "READ_ONLY" for tenant "tenant-1"
    When I POST /api/v1/work-items/wi-100/transitions with body {"transition":"close-as-resolved"}
    Then the response status is 403

  Scenario: Authenticated user retrieves the audit trail
    Given the audit trail for work item "wi-100" has 2 entries
    And I am authenticated as user "user-1" with role "ANALYST" for tenant "tenant-1"
    When I GET /api/v1/audit/work-items/wi-100
    Then the response status is 200
    And the response contains 2 audit entries

  Scenario: Transition with additional fields merges values into work item
    Given I am authenticated as user "user-1" with role "ANALYST" for tenant "tenant-1"
    When I POST /api/v1/work-items/wi-100/transitions with body {"transition":"close-as-resolved","additionalFields":{"resolution.reason":"Duplicate trade"}}
    Then the response status is 200
    And the response contains work item status "CLOSED"
