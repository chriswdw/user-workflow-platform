Feature: Audit trail

  Background:
    Given tenant "tenant-1"

  Scenario: Appending an entry makes it retrievable
    When an INGESTION audit entry is appended for work item "wi-001"
    Then the audit trail for work item "wi-001" contains 1 entry
    And the entry has event type "INGESTION"

  Scenario: Multiple entries are returned in timestamp order
    When an INGESTION audit entry is appended for work item "wi-002" at "2024-01-01T10:00:00Z"
    And a STATE_TRANSITION audit entry is appended for work item "wi-002" at "2024-01-01T11:00:00Z"
    And a FIELD_UPDATE audit entry is appended for work item "wi-002" at "2024-01-01T10:30:00Z"
    Then the audit trail for work item "wi-002" contains 3 entries
    And the entries are in timestamp order

  Scenario: Query returns empty trail for unknown work item
    When the audit trail for work item "wi-unknown" is queried
    Then the trail is empty

  Scenario: Entry for one work item does not appear in another's trail
    When an INGESTION audit entry is appended for work item "wi-003"
    Then the audit trail for work item "wi-004" contains 0 entries

  Scenario: Filter by event type returns only matching entries
    When an INGESTION audit entry is appended for work item "wi-005"
    And a STATE_TRANSITION audit entry is appended for work item "wi-005"
    And a FIELD_UPDATE audit entry is appended for work item "wi-005"
    When the audit trail for work item "wi-005" is queried filtering by event type "STATE_TRANSITION"
    Then the trail contains 1 entry
    And the entry has event type "STATE_TRANSITION"

  Scenario: Filter by timestamp range returns entries within range
    When an INGESTION audit entry is appended for work item "wi-006" at "2024-01-01T09:00:00Z"
    And a STATE_TRANSITION audit entry is appended for work item "wi-006" at "2024-01-01T10:00:00Z"
    And a FIELD_UPDATE audit entry is appended for work item "wi-006" at "2024-01-01T11:00:00Z"
    When the audit trail for work item "wi-006" is queried from "2024-01-01T09:30:00Z" to "2024-01-01T10:30:00Z"
    Then the trail contains 1 entry
    And the entry has event type "STATE_TRANSITION"
