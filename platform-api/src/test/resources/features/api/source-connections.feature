Feature: Source connection API

  Scenario: Admin creates a Kafka source connection
    Given I am authenticated as user "admin" with role "PLATFORM_ADMIN" for tenant "platform"
    When I POST /api/v1/admin/source-connections with body {"name":"kafka-prod","displayName":"Kafka Prod","connectionType":"KAFKA","config":{"bootstrapServers":"broker:9092","topicName":"trades"},"credentialsRef":"vault/kafka-prod"}
    Then the response status is 201
    And the response contains a "id" field

  Scenario: Admin creates a DB_POLL source connection
    Given I am authenticated as user "admin" with role "PLATFORM_ADMIN" for tenant "platform"
    When I POST /api/v1/admin/source-connections with body {"name":"db-poll-prod","displayName":"DB Poll Prod","connectionType":"DB_POLL","config":{"jdbcUrl":"jdbc:postgresql://host:5432/db","query":"SELECT * FROM items","pollIntervalSeconds":30}}
    Then the response status is 201
    And the response contains a "id" field

  Scenario: Creating a Kafka connection with missing bootstrapServers returns 400
    Given I am authenticated as user "admin" with role "PLATFORM_ADMIN" for tenant "platform"
    When I POST /api/v1/admin/source-connections with body {"name":"bad","displayName":"Bad","connectionType":"KAFKA","config":{"topicName":"trades"}}
    Then the response status is 400

  Scenario: Creating a DB_POLL connection with inline credentials in jdbcUrl returns 400
    Given I am authenticated as user "admin" with role "PLATFORM_ADMIN" for tenant "platform"
    When I POST /api/v1/admin/source-connections with body {"name":"bad","displayName":"Bad","connectionType":"DB_POLL","config":{"jdbcUrl":"jdbc:postgresql://user:secret@host:5432/db","query":"SELECT 1","pollIntervalSeconds":60}}
    Then the response status is 400

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
    When I POST /api/v1/admin/source-connections with body {"name":"x","displayName":"X","connectionType":"KAFKA","config":{"bootstrapServers":"broker:9092","topicName":"t"},"credentialsRef":"ref"}
    Then the response status is 403

  Scenario: Non-admin listing all source connections returns 403
    Given I am authenticated as user "alice" with role "ANALYST" for tenant "tenant-1"
    When I GET /api/v1/admin/source-connections
    Then the response status is 403

  Scenario: Non-admin updating a source connection returns 403
    Given I am authenticated as user "alice" with role "ANALYST" for tenant "tenant-1"
    And a source connection "conn-1" of type "KAFKA" exists
    When I PATCH /api/v1/admin/source-connections/conn-1 with body {"displayName":"Hacked Name"}
    Then the response status is 403

  Scenario: Non-admin deleting a source connection returns 403
    Given I am authenticated as user "alice" with role "ANALYST" for tenant "tenant-1"
    And a source connection "conn-1" of type "KAFKA" exists
    When I DELETE /api/v1/admin/source-connections/conn-1
    Then the response status is 403

  Scenario: Non-admin granting tenant access returns 403
    Given I am authenticated as user "alice" with role "ANALYST" for tenant "tenant-1"
    And a source connection "conn-1" of type "KAFKA" exists
    When I POST /api/v1/admin/source-connections/conn-1/access with body {"tenantId":"tenant-2"}
    Then the response status is 403

  Scenario: Non-admin revoking tenant access returns 403
    Given I am authenticated as user "alice" with role "ANALYST" for tenant "tenant-1"
    And a source connection "conn-1" of type "KAFKA" exists
    And tenant "tenant-1" has access to source connection "conn-1"
    When I DELETE /api/v1/admin/source-connections/conn-1/access/tenant-1
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
