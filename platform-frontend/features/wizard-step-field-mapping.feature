@wizard-field-mapping
Feature: Field mapping and blotter column steps render real business behaviour

  These scenarios render the actual StepFieldMapping and StepBlotterConfig components, covering
  two things the pure isStepComplete()/store scenarios don't reach: the field-mapping row
  add/remove focus-stability behaviour (the ref-based stable-key mechanism), and the interplay
  where StepBlotterConfig only offers fields that were actually mapped in the previous step.

  Scenario: Editing one mapping row's target path is not disturbed by adding another row
    Given the wizard has sample fields "trade.ref,trade.amount"
    When I render the field mapping step
    And I type "custom.path" into the target path of mapping row 1
    And I click Add field
    Then the field mapping table has 3 rows
    And mapping row 1's target path still reads "custom.path"

  Scenario: Editing one mapping row's target path is not disturbed by removing another row
    Given the wizard has sample fields "trade.ref,trade.amount,trade.status"
    When I render the field mapping step
    And I type "custom.path" into the target path of mapping row 1
    And I remove mapping row 3
    Then the field mapping table has 2 rows
    And mapping row 1's target path still reads "custom.path"

  Scenario: Only fields that were mapped in the field mapping step are offered as blotter columns
    Given the wizard has field mappings for "trade.ref"
    When I render the blotter config step
    Then only "trade.ref" is offered as a blotter column candidate

  Scenario: Selecting a blotter column reveals its display configuration inputs
    Given the wizard has field mappings for "trade.ref"
    When I render the blotter config step
    And I check the blotter column checkbox for "trade.ref"
    Then a header label input appears for the "trade.ref" column
