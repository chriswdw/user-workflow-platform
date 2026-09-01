@wizard-shell
Feature: Wizard shell orchestrates step navigation

  These scenarios render the actual WizardShell component through the shared jsdom + React
  Testing Library harness, exercising real navigation-gating and progress-step behaviour rather
  than just the pure isStepComplete()/store logic covered in wizard-foundation.feature.

  Scenario: Wizard opens on step 1 with Basic Info active and Next disabled until valid
    When I render the wizard shell
    Then the wizard shows step "Basic Info" as active
    And the Next button is disabled

  Scenario: Next becomes enabled once step 1's validation passes
    Given the wizard shell has workflowType "TRADE_BREAK" and displayName "Trade Break"
    When I render the wizard shell
    Then the Next button is enabled

  Scenario: Clicking a completed step in the progress bar jumps directly to it
    Given the wizard is on step 3
    When I render the wizard shell
    And I click the "Basic Info" progress step
    Then the wizard store currentStep is 1

  Scenario: Pressing Escape closes the wizard
    When I render the wizard shell
    And I press Escape
    Then the wizard shell onClose callback fires
