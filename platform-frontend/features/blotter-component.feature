@blotter
Feature: Blotter grid renders work items with masking and selection

  These scenarios render the actual Blotter component (ag-grid) through the shared jsdom + React
  Testing Library harness, exercising the real column mapping, field resolution, and masking
  logic that features/blotter.feature only exercises indirectly via the pure helper functions.

  Scenario: Blotter renders configured columns and resolved field values
    Given a blotter config with columns for "status" and "trade.ref"
    And one work item with field "trade.ref" set to "TRD-001"
    When I render the blotter
    Then the blotter header shows "status" and "trade.ref"
    And the blotter shows a cell with text "TRD-001"

  Scenario: A field masked for the viewer's role renders as masked instead of the real value
    Given a blotter column "trade.ref" masked for roles "SUPERVISOR"
    And one work item with field "trade.ref" set to "TRD-001"
    And the current blotter viewer has role "ANALYST"
    When I render the blotter
    Then the blotter shows a masked cell instead of "TRD-001"

  Scenario: A field masked for a role the viewer holds renders unmasked
    Given a blotter column "trade.ref" masked for roles "SUPERVISOR"
    And one work item with field "trade.ref" set to "TRD-001"
    And the current blotter viewer has role "SUPERVISOR"
    When I render the blotter
    Then the blotter shows a cell with text "TRD-001"

  Scenario: Clicking a row selects that work item
    Given a blotter config with columns for "status" and "trade.ref"
    And one work item with status "UNDER_REVIEW"
    When I render the blotter
    And I click the first blotter row
    Then onSelectItem fires with the clicked row's id
