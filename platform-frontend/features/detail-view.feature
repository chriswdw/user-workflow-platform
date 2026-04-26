Feature: Detail view action visibility and rendering

  Scenario: Action is visible when work item is in the correct state
    Given an action visible in states "UNDER_REVIEW"
    And the work item is in state "UNDER_REVIEW"
    Then the action should be visible

  Scenario: Action is hidden when work item is in the wrong state
    Given an action visible in states "UNDER_REVIEW"
    And the work item is in state "CLOSED"
    Then the action should not be visible

  Scenario: Action visible in one of multiple allowed states
    Given an action visible in states "UNDER_REVIEW,ESCALATED"
    And the work item is in state "ESCALATED"
    Then the action should be visible

  Scenario: Action with role restriction visible for authorised role
    Given an action visible in states "UNDER_REVIEW" for roles "ANALYST"
    And the work item is in state "UNDER_REVIEW"
    And the current user has role "ANALYST"
    Then the action should be visible

  Scenario: Action with role restriction hidden for unauthorised role
    Given an action visible in states "UNDER_REVIEW" for roles "ANALYST"
    And the work item is in state "UNDER_REVIEW"
    And the current user has role "READ_ONLY"
    Then the action should not be visible

  Scenario: Field with CURRENCY formatter displays formatted value
    Given a field value of "1000000"
    When I format the value with formatter "CURRENCY"
    Then the formatted value is "$1,000,000.00"

  Scenario: Field with DATE formatter displays formatted value
    Given a field value of "2026-04-30"
    When I format the value with formatter "DATE"
    Then the formatted value is not raw "2026-04-30"

  Scenario: Field with PERCENTAGE formatter displays formatted value
    Given a field value of "75"
    When I format the value with formatter "PERCENTAGE"
    Then the formatted value is "75%"

  Scenario: Action with input fields has inputFields on the action config
    Given an action with input field "resolution.reason" of type "TEXT" labelled "Reason"
    Then the action has 1 input field
    And the input field label is "Reason"
    And the input field type is "TEXT"

  Scenario: Action input field can be required
    Given an action with input field "resolution.reason" of type "TEXT" labelled "Reason"
    And the input field is required
    Then the input field is marked required

  Scenario: Action input field SELECT has options
    Given an action with input field "resolution.category" of type "SELECT" labelled "Category"
    And the input field has options "DUPLICATE,INCORRECT_SSI,OTHER"
    Then the input field has 3 options

  Scenario: Maker-checker banner shown when checker pending
    Given a work item with pendingCheckerId "user-2" and pendingCheckerTransition "approve"
    Then the maker checker transition is "approve"

  Scenario: No maker-checker banner when no checker pending
    Given a work item with no pending checker
    Then the work item has no pending checker
