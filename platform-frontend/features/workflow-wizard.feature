Feature: Workflow wizard — step validation and submission behaviour

  Background:
    Given the wizard store is reset

  # ── Step 1 ──────────────────────────────────────────────────────────────────

  Scenario: Step 1 is invalid with empty workflowType
    Given the wizard workflowType is "" and displayName is "Trade Lifecycle"
    Then step 1 is not complete

  Scenario: Step 1 is invalid with lowercase workflowType
    Given the wizard workflowType is "trade_lifecycle" and displayName is "Trade Lifecycle"
    Then step 1 is not complete

  Scenario: Step 1 is valid with correct pattern and displayName
    Given the wizard workflowType is "TRADE_LIFECYCLE" and displayName is "Trade Lifecycle"
    Then step 1 is complete

  # ── Step 2 ──────────────────────────────────────────────────────────────────

  Scenario: Step 2 is invalid when no sourceType is set
    Given the wizard sourceType is not set
    Then step 2 is not complete

  Scenario: Step 2 is invalid when KAFKA selected but no connection chosen
    Given the wizard sourceType is "KAFKA" with no connection
    Then step 2 is not complete

  Scenario: Step 2 is valid when MANUAL_UPLOAD selected with no connection
    Given the wizard sourceType is "MANUAL_UPLOAD" with no connection
    Then step 2 is complete

  Scenario: Step 2 is valid when KAFKA selected with a connection
    Given the wizard sourceType is "KAFKA" with connectionId "conn-001"
    Then step 2 is complete

  # ── Step 3 ──────────────────────────────────────────────────────────────────

  Scenario: Step 3 is always valid regardless of sampleFields
    Given the wizard store is reset
    Then step 3 is complete

  # ── Step 4 ──────────────────────────────────────────────────────────────────

  Scenario: Step 4 is invalid with no field mappings
    Given the wizard has no field mappings
    Then step 4 is not complete

  Scenario: Step 4 is invalid when mappings exist but no idempotencyKey set
    Given the wizard has 1 field mapping with no idempotencyKey
    Then step 4 is not complete

  Scenario: Step 4 is valid with field mappings and idempotencyKey set
    Given the wizard has 1 field mapping with idempotencyKey "trade.ref"
    Then step 4 is complete

  # ── Step 5 ──────────────────────────────────────────────────────────────────

  Scenario: Step 5 is invalid with no blotter columns selected
    Given the wizard has no blotter columns
    Then step 5 is not complete

  Scenario: Step 5 is valid with at least one column
    Given the wizard has 1 blotter column
    Then step 5 is complete

  # ── Step 6 ──────────────────────────────────────────────────────────────────

  Scenario: Step 6 is invalid with no detail sections
    Given the wizard has no detail sections
    Then step 6 is not complete

  Scenario: Step 6 is valid with at least one section
    Given the wizard has 1 detail section
    Then step 6 is complete

  # ── Revision / Resume ───────────────────────────────────────────────────────

  Scenario: Revision flow sets revisingSubmissionId on hydration
    Given a submission with id "sub-rev-01" and currentStep 4 and no draftConfigs
    When I hydrate the store for revision
    Then the store revisingSubmissionId is "sub-rev-01"
    And the store currentStep is 1

  Scenario: Resume flow opens at currentStep plus one
    Given a submission with id "sub-res-01" and currentStep 3 and no draftConfigs
    When I hydrate the store for resume
    Then the store currentStep is 4
    And the store revisingSubmissionId is null

  Scenario: buildSubmissionPayload includes blotter and section data
    Given the wizard has 1 blotter column
    And the wizard has 1 detail section
    When I build the submission payload
    Then the payload blotterColumns has 1 entry
    And the payload detailSections has 1 entry
