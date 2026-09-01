@wizard-sample-upload
Feature: Sample upload step renders real business behaviour

  These scenarios render the actual StepSampleUpload component, covering the pre-loaded-from-draft
  banner/clear flow and real file upload handling (JSON field extraction, rejecting unsupported
  file types) — none of which the pure fieldExtractor.feature scenarios exercise through the UI.

  Scenario: A resumed draft with sample fields already set shows the pre-loaded banner
    Given the wizard already has sample fields "trade.ref,trade.amount"
    When I render the sample upload step
    Then the sample upload step shows 2 pre-loaded fields

  Scenario: Clearing pre-loaded fields returns to the upload prompt
    Given the wizard already has sample fields "trade.ref,trade.amount"
    When I render the sample upload step
    And I click Clear and re-upload
    Then the sample upload step shows the upload prompt with no pre-loaded fields
    And the wizard store sampleFields is empty

  Scenario: Uploading a valid JSON sample file extracts its field paths into the store
    When I render the sample upload step
    And I upload a sample JSON file with content "{\"trade\": {\"ref\": \"TRD-001\"}}"
    Then the wizard store sampleFields includes "trade.ref"

  Scenario: Uploading an unsupported file type does not populate sample fields
    When I render the sample upload step
    And I upload an unsupported file type
    Then the wizard store sampleFields is empty
