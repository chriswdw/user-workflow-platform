Feature: Blotter field resolution and masking

  Scenario: Field resolved from nested dot-notation path
    Given a work item with field "trade.ref" set to "TRD-001"
    When I resolve "trade.ref" from the work item
    Then the resolved value is "TRD-001"

  Scenario: Deeply nested field resolved correctly
    Given a work item with field "address.city" set to "London"
    When I resolve "address.city" from the work item
    Then the resolved value is "London"

  Scenario: Missing field path returns undefined
    Given a work item with no fields
    When I resolve "nonexistent.field" from the work item
    Then the resolved value is undefined

  Scenario: Field masked for user without required role
    Given a field value "TRD-001"
    And the field requires role "ANALYST" to view unmasked
    And the current user has role "READ_ONLY"
    When I apply field masking
    Then the displayed value is "***"

  Scenario: Field visible for user with required role
    Given a field value "TRD-001"
    And the field requires role "ANALYST" to view unmasked
    And the current user has role "ANALYST"
    When I apply field masking
    Then the displayed value is "TRD-001"

  Scenario: Field with no masking roles is always visible
    Given a field value "PUBLIC-DATA"
    And the field has no masking roles
    And the current user has role "READ_ONLY"
    When I apply field masking
    Then the displayed value is "PUBLIC-DATA"
