Feature: Source connection API

  Scenario: Admin creates a source connection
    Given I am authenticated as user "admin" with role "PLATFORM_ADMIN" for tenant "platform"
    When I POST /api/v1/admin/source-connections with body {"name":"kafka-prod","displayName":"Kafka Prod","connectionType":"KAFKA","config":{},"credentialsRef":"vault/kafka-prod"}
    Then the response status is 201
    And the response contains a "id" field

  Scenario: Admin lists all source connections
    Given I am authenticated as user "admin" with role "PLATFORM_ADMIN" for tenant "platform"
    And a source connection "kafka-prod" of type "KAFKA" exists
    When I GET /api/v1/admin/source-connections
    Then the response status is 200
    And the response is a non-empty JSON array

  Scenario: Admin grants tenant access
    Given I am authenticated as user "admin" with role "PLATFORM_ADMIN" for tenant "platform"
    And a source connection "conn-1" of type "KAFKA" exists
    When I POST /api/v1/admin/source-connections/conn-1/access with body {"tenantId":"tenant-1"}
    Then the response status is 204

  Scenario: Admin revokes tenant access
    Given I am authenticated as user "admin" with role "PLATFORM_ADMIN" for tenant "platform"
    And a source connection "conn-1" of type "KAFKA" exists
    And tenant "tenant-1" has access to source connection "conn-1"
    When I DELETE /api/v1/admin/source-connections/conn-1/access/tenant-1
    Then the response status is 204

  Scenario: Analyst lists accessible connections filtered by type
    Given I am authenticated as user "alice" with role "ANALYST" for tenant "tenant-1"
    And a source connection "conn-1" of type "KAFKA" exists
    And tenant "tenant-1" has access to source connection "conn-1"
    When I GET /api/v1/source-connections?type=KAFKA
    Then the response status is 200
    And the response is a non-empty JSON array

  Scenario: Non-admin accessing admin endpoint returns 403
    Given I am authenticated as user "alice" with role "ANALYST" for tenant "tenant-1"
    When I POST /api/v1/admin/source-connections with body {"name":"x","displayName":"X","connectionType":"KAFKA","config":{},"credentialsRef":"ref"}
    Then the response status is 403

  Scenario: Admin updates a source connection
    Given I am authenticated as user "admin" with role "PLATFORM_ADMIN" for tenant "platform"
    And a source connection "conn-1" of type "KAFKA" exists
    When I PATCH /api/v1/admin/source-connections/conn-1 with body {"displayName":"Updated Name"}
    Then the response status is 200
    And the response contains a "id" field

  Scenario: Admin deletes a source connection
    Given I am authenticated as user "admin" with role "PLATFORM_ADMIN" for tenant "platform"
    And a source connection "conn-1" of type "KAFKA" exists
    When I DELETE /api/v1/admin/source-connections/conn-1
    Then the response status is 204

  Scenario: Unauthenticated access returns 401
    Given I am not authenticated
    When I GET /api/v1/source-connections
    Then the response status is 401
