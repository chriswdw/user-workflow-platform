Feature: Work item ingestion

  Background:
    Given an ingestion config for tenant "tenant-1" workflow "SETTLEMENT_EXCEPTION" source "KAFKA" policy "IGNORE" with mappings:
      | sourceField       | targetField       | required |
      | TRADE_REF         | trade.ref         | true     |
      | COUNTERPARTY_NAME | counterparty.name | false    |
    And the routing port assigns group "group-ops" for workflow type "SETTLEMENT_EXCEPTION"

  Scenario: Successful ingestion creates a work item with mapped fields
    When a "KAFKA" record is ingested for workflow "SETTLEMENT_EXCEPTION" with fields:
      | TRADE_REF         | TRD-001  |
      | COUNTERPARTY_NAME | Barclays |
    Then the ingestion result is Created
    And the work item has field "trade.ref" equal to "TRD-001"
    And the work item has field "counterparty.name" equal to "Barclays"
    And the work item is assigned to group "group-ops"
    And an INGESTION audit entry is written

  Scenario: Duplicate record is silently discarded
    Given a record with idempotency key "TRD-001" has already been ingested for workflow "SETTLEMENT_EXCEPTION"
    When a "KAFKA" record is ingested for workflow "SETTLEMENT_EXCEPTION" with fields:
      | TRADE_REF | TRD-001 |
    Then the ingestion result is Duplicate
    And a DUPLICATE_INGESTION_DISCARDED audit entry is written
    And no new work item is saved

  Scenario: Record with missing required field is rejected
    When a "KAFKA" record is ingested for workflow "SETTLEMENT_EXCEPTION" with fields:
      | COUNTERPARTY_NAME | Barclays |
    Then the ingestion result is Rejected with reason mentioning "TRADE_REF"

  Scenario: Field mapping produces nested WorkItem fields from flat source columns
    Given an ingestion config for tenant "tenant-1" workflow "SETTLEMENT_EXCEPTION" source "DB_POLL" policy "IGNORE" with mappings:
      | sourceField    | targetField   | required |
      | ADDRESS_LINE_1 | address.line1 | false    |
      | ADDRESS_CITY   | address.city  | false    |
    When a "DB_POLL" record is ingested for workflow "SETTLEMENT_EXCEPTION" with fields:
      | ADDRESS_LINE_1 | 26 Wilton Crescent |
      | ADDRESS_CITY   | London             |
    Then the ingestion result is Created
    And the work item has field "address.line1" equal to "26 Wilton Crescent"
    And the work item has field "address.city" equal to "London"

  Scenario: Unknown column with REJECT policy causes rejection
    Given an ingestion config for tenant "tenant-1" workflow "SETTLEMENT_EXCEPTION" source "FILE_UPLOAD" policy "REJECT" with mappings:
      | sourceField | targetField | required |
      | TRADE_REF   | trade.ref   | false    |
    When a "FILE_UPLOAD" record is ingested for workflow "SETTLEMENT_EXCEPTION" with fields:
      | TRADE_REF   | TRD-001    |
      | UNKNOWN_COL | some-value |
    Then the ingestion result is Rejected with reason mentioning "UNKNOWN_COL"

  Scenario: Unknown column with IGNORE policy is silently dropped
    Given an ingestion config for tenant "tenant-1" workflow "SETTLEMENT_EXCEPTION" source "FILE_UPLOAD" policy "IGNORE" with mappings:
      | sourceField | targetField | required |
      | TRADE_REF   | trade.ref   | false    |
    When a "FILE_UPLOAD" record is ingested for workflow "SETTLEMENT_EXCEPTION" with fields:
      | TRADE_REF   | TRD-001    |
      | UNKNOWN_COL | some-value |
    Then the ingestion result is Created
    And the work item has field "trade.ref" equal to "TRD-001"
    And the work item fields do not contain key "UNKNOWN_COL"

  Scenario Outline: Source type is preserved on the created work item
    Given an ingestion config for tenant "tenant-1" workflow "SETTLEMENT_EXCEPTION" source "<sourceType>" policy "IGNORE" with mappings:
      | sourceField | targetField | required |
      | TRADE_REF   | trade.ref   | false    |
    When a "<sourceType>" record is ingested for workflow "SETTLEMENT_EXCEPTION" with fields:
      | TRADE_REF | TRD-001 |
    Then the ingestion result is Created
    And the work item source type is "<sourceType>"

    Examples:
      | sourceType  |
      | KAFKA       |
      | DB_POLL     |
      | FILE_UPLOAD |
