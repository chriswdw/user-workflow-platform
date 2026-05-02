Feature: Wizard foundation — field extraction and store behaviour

  Scenario: extractFieldPaths yields leaf dot-paths from nested JSON
    Given the JSON object '{"trade":{"ref":"T001","amount":100.5},"status":"OPEN"}'
    When I extract field paths from the object
    Then the extracted paths include "trade.ref"
    And the extracted paths include "trade.amount"
    And the extracted paths include "status"

  Scenario: CSV header extraction yields column names
    Given the CSV string "id,trade.ref,amount,status"
    When I extract CSV headers
    Then the extracted paths include "id"
    And the extracted paths include "trade.ref"
    And the extracted paths include "amount"
    And the extracted paths include "status"

  Scenario: JSON and CSV file types are supported
    Given a file named "sample.json"
    When I check if the file type is supported
    Then the file type is supported

  Scenario: xlsx file type is not supported
    Given a file named "data.xlsx"
    When I check if the file type is supported
    Then the file type is not supported

  Scenario: buildSubmissionPayload includes auto-generated workflowConfig
    Given the wizard workflowType is "TRADE_LIFECYCLE" and displayName is "Trade Lifecycle"
    When I build the submission payload
    Then the payload workflowConfig initialState is "UNDER_REVIEW"
    And the payload workflowConfig has 2 states
    And the payload workflowConfig has 1 transition named "close"

  Scenario: hydrateForResume opens wizard at currentStep plus one
    Given a submission with id "sub-001" and currentStep 2 and no draftConfigs
    When I hydrate the store for resume
    Then the store currentStep is 3
    And the store submissionId is "sub-001"
    And the store revisingSubmissionId is null

  Scenario: hydrateForRevision opens wizard at step 1 with revisingSubmissionId set
    Given a submission with id "sub-001" and currentStep 3 and no draftConfigs
    When I hydrate the store for revision
    Then the store currentStep is 1
    And the store revisingSubmissionId is "sub-001"
    And the store submissionId is "sub-001"

  Scenario: sampleFields populated from draftConfigs during resume hydration
    Given a submission with draftConfigs containing sampleFields "trade.ref,amount"
    When I hydrate the store for resume
    Then the store sampleFields is non-empty
