@wizard-detail-review
Feature: Detail view config and review steps render real business behaviour

  StepDetailViewConfig scenarios cover section add/remove and field assignment; StepReview
  scenarios cover the submitError parsing that surfaces a "Go to that step" link only when the
  server's error message actually names a step number, and wires it to onGoToStep correctly —
  logic with no equivalent coverage anywhere else in the suite.

  Scenario: Adding a section renders a section card
    When I render the detail view config step
    And I click Add section
    Then the detail view config has 1 section

  Scenario: Assigning a field to a section reveals its label input
    Given the wizard has field mappings for detail view "trade.ref"
    When I render the detail view config step
    And I click Add section
    And I check the detail section field checkbox for "trade.ref"
    Then a label input appears for the "trade.ref" detail field

  Scenario: Review step summarises the field mappings count
    Given the wizard has field mappings for detail view "trade.ref,trade.amount"
    When I render the review step
    Then the review shows 2 field mappings in the summary

  Scenario: A submitError naming a step shows a "Go to that step" link
    Given the review step has submitError "Incomplete — return to step 4"
    When I render the review step
    Then the review shows the error banner with "Incomplete — return to step 4"
    And the review shows a "Go to that step" link

  Scenario: A submitError not naming a step shows no "Go to that step" link
    Given the review step has submitError "Network error, please retry."
    When I render the review step
    Then the review shows the error banner with "Network error, please retry."
    And the review does not show a "Go to that step" link

  Scenario: Clicking "Go to that step" calls onGoToStep with the parsed step number
    Given the review step has submitError "Incomplete — return to step 4"
    When I render the review step
    And I click "Go to that step"
    Then onGoToStep fires with 4
