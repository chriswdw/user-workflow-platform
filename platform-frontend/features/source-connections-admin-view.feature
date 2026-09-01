@source-connections-admin
Feature: Source connections admin view lets admins create connections and manage tenant access

  Covers the type-conditional add-connection form (Kafka/DB Poll/File Share fields), its
  Save-disabled-until-valid gating, and the Manage Access modal's grant flow — real business logic
  with no equivalent coverage elsewhere in the suite.

  Scenario: Existing connections are listed
    Given an admin-visible source connection "Kafka Primary" of type "KAFKA"
    When I render the source connections admin view
    Then the connections table lists "Kafka Primary"

  Scenario: The add connection form defaults to Kafka fields
    When I render the source connections admin view
    And I click Add Connection
    Then the add connection form shows Kafka fields

  Scenario: Switching connection type changes the rendered fields
    When I render the source connections admin view
    And I click Add Connection
    And I select connection type "DB_POLL"
    Then the add connection form shows DB poll fields

  Scenario: Save is disabled until a display name is entered
    When I render the source connections admin view
    And I click Add Connection
    Then the Save button is disabled

  Scenario: Saving a new connection sends a create request
    When I render the source connections admin view
    And I click Add Connection
    And I type "Kafka Secondary" as the connection display name
    And I click Save on the add connection form
    Then a create connection request is sent with displayName "Kafka Secondary"

  Scenario: Clicking Manage Access opens the access modal for that connection
    Given an admin-visible source connection "Kafka Primary" of type "KAFKA"
    When I render the source connections admin view
    And I click Manage Access
    Then the access modal is shown for "Kafka Primary"

  Scenario: Granting access sends a request with the entered tenant id
    Given an admin-visible source connection "Kafka Primary" of type "KAFKA"
    When I render the source connections admin view
    And I click Manage Access
    And I type "tenant-9" as the grant tenant id
    And I click Grant Access
    Then a grant access request is sent for tenant "tenant-9"
