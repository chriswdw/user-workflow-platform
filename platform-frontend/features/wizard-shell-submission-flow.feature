@wizard-shell-submission-flow
Feature: Wizard shell submission flow — create, save, submit, revise

  These scenarios render the actual WizardShell component and drive its Next/Save Draft/Submit
  buttons through real React Query mutations (stubbed at the HTTP client), covering the
  create-submission / save-draft / submit-for-approval / revise-submission call sites that
  wizard-shell-component.feature's pure navigation scenarios don't reach.

  Scenario: Clicking Next on step 1 creates the submission and advances to step 2
    Given the wizard shell has workflowType "TRADE_BREAK" and displayName "Trade Break"
    And submission creation will succeed with id "sub-created-1"
    When I render the wizard shell
    And I click Next
    Then the submission is created with workflowType "TRADE_BREAK"
    And the wizard store currentStep is 2
    And the wizard store submissionId is "sub-created-1"

  Scenario: A duplicate workflow type on step 1 shows a conflict error and does not advance
    Given the wizard shell has workflowType "TRADE_BREAK" and displayName "Trade Break"
    And submission creation will fail with a 409 conflict
    When I render the wizard shell
    And I click Next
    Then the wizard footer shows an error containing "already exists"
    And the wizard store currentStep is 1

  Scenario: A generic submission creation failure on step 1 shows the server's error message
    Given the wizard shell has workflowType "TRADE_BREAK" and displayName "Trade Break"
    And submission creation will fail with message "Backend unavailable"
    When I render the wizard shell
    And I click Next
    Then the wizard footer shows an error containing "Backend unavailable"
    And the wizard store currentStep is 1

  Scenario: Clicking Next on a mid-wizard step saves the draft and advances
    Given the wizard is on step 2 with an existing submission "sub-1"
    And the wizard sourceType is "MANUAL_UPLOAD" with no connection
    And draft saving will succeed
    When I render the wizard shell
    And I click Next
    Then a draft was saved for step 2
    And the wizard store currentStep is 3

  Scenario: A draft save failure on a mid-wizard step shows an error and does not advance
    Given the wizard is on step 2 with an existing submission "sub-1"
    And the wizard sourceType is "MANUAL_UPLOAD" with no connection
    And draft saving will fail with message "Network error"
    When I render the wizard shell
    And I click Next
    Then the wizard footer shows an error containing "Network error"
    And the wizard store currentStep is 2

  Scenario: The Save Draft button saves the current draft without advancing
    Given the wizard is on step 2 with an existing submission "sub-1"
    And the wizard sourceType is "MANUAL_UPLOAD" with no connection
    And draft saving will succeed
    When I render the wizard shell
    And I click the "Save Draft" button
    Then a draft was saved for step 2
    And the wizard store currentStep is 2

  Scenario: Submitting the completed wizard submits for approval and closes it
    Given the wizard is on step 7 with an existing submission "sub-1"
    And submit for approval will succeed
    When I render the wizard shell
    And I click the "Submit for Approval" button
    Then the submission was submitted for approval
    And the wizard shell onClose callback fires

  Scenario: An incomplete submission shows which step to revisit and does not close the wizard
    Given the wizard is on step 7 with an existing submission "sub-1"
    And submit for approval will fail with message "incomplete, return to step 4"
    When I render the wizard shell
    And I click the "Submit for Approval" button
    Then the wizard footer shows an error containing "Incomplete — return to step 4"
    And the wizard shell onClose callback does not fire

  Scenario: Submitting a revision first revises the submission then submits it for approval
    Given the wizard is on step 7 revising submission "sub-1"
    And revision and submit for approval will both succeed
    When I render the wizard shell
    And I click the "Submit for Approval" button
    Then the submission was revised before being submitted for approval
    And the wizard shell onClose callback fires
